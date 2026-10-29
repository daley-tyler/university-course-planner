import test from 'node:test';
import assert from 'node:assert/strict';
import { copyPlanAsWhatIf, buildTerms, checkPlan, hasPrerequisiteViolation, nextTermOrder } from '../src/store/planLogic.ts';

function fixture() {
    return { id: 'plan', name: 'Original', kind: 'CURRENT', programs: [{id: 'p', name: 'Program', catalogYear: '2025'}],
        courses: [
            {id: 'c1', code: 'DEMO 101', title: 'Intro', credits: 3, status: 'COMPLETED', scheduledTermId: null},
            {id: 'c2', code: 'DEMO 201', title: 'Next', credits: 3, status: 'PLANNED', scheduledTermId: 'fall', prereqs: ['DEMO 101']},
            {id: 'c3', code: 'DEMO 301', title: 'Later', credits: 3, status: 'PLANNED', scheduledTermId: 'spring'},
        ], terms: [{id: 'fall', name: 'Fall', order: 1, courseIds: ['c2'], creditCap: 18},
                   {id: 'spring', name: 'Spring', order: 2, courseIds: ['c3'], creditCap: 18}] };
}
let serial=0;
const id = (prefix) => `${prefix}-${++serial}`;

test('what-if copies retain schedules through normalization and JSON reload', () => {
    const source = fixture(); const before=structuredClone(source);
    const copy = copyPlanAsWhatIf(source, 'copy', id);
    assert.equal(copy.parentPlanId, source.id); assert.equal(copy.kind, 'WHAT_IF');
    assert.deepEqual(source, before);
    for (const term of copy.terms) {
        assert.ok(!source.terms.some(t=>t.id===term.id));
        for (const courseId of term.courseIds) {
            assert.equal(copy.courses.find(c=>c.id===courseId).scheduledTermId, term.id);
        }
    }
    const reload=JSON.parse(JSON.stringify(copy));
    assert.deepEqual(buildTerms(reload.terms, reload.courses), copy.terms);
    assert.deepEqual(copy.terms.map(t=>t.courseIds.length), [1,1]);
    assert.equal(copy.courses[0].scheduledTermId,null);
});
test('copies do not share mutable arrays or programs with their source', () => {
    const source=fixture(); const copy=copyPlanAsWhatIf(source,'copy',id);
    copy.programs[0].name='Changed';copy.courses[1].prereqs.push('DEMO 999');copy.terms[0].courseIds.pop();
    assert.equal(source.programs[0].name,'Program');assert.deepEqual(source.courses[1].prereqs,['DEMO 101']);
    assert.deepEqual(source.terms[0].courseIds,['c2']);
});
test('dangling assignments become unscheduled in copies', () => {
    const source=fixture();source.courses[1].scheduledTermId='missing';
    const copy=copyPlanAsWhatIf(source,'copy',id);
    assert.equal(copy.courses[1].scheduledTermId,null);assert.deepEqual(copy.terms[0].courseIds,[]);
});
test('completed courses need no scheduled term and satisfy the basic prerequisite check', () => {
    const plan=fixture();assert.equal(checkPlan(plan,id).issues.length,0);
    assert.equal(hasPrerequisiteViolation(plan.courses[1],plan,'fall'),false);
});
test('missing, same-term and later prerequisites warn; earlier prerequisites pass', () => {
    const plan=fixture();const course=plan.courses[2];course.prereqs=['DEMO 201'];
    assert.equal(hasPrerequisiteViolation(course,plan,'spring'),false);
    assert.equal(hasPrerequisiteViolation(course,plan,'fall'),true);
    course.prereqs=['MISSING 100'];assert.equal(hasPrerequisiteViolation(course,plan,'spring'),true);
    course.prereqs=['DEMO 301'];assert.equal(hasPrerequisiteViolation(plan.courses[1],plan,'fall',['DEMO 301']),false);
    plan.courses[1].prereqs=['DEMO 301'];assert.equal(hasPrerequisiteViolation(plan.courses[1],plan,'fall'),true);
});
test('basic checks report all unscheduled planned courses and dangling terms', () => {
    const plan=fixture();plan.courses=Array.from({length:6},(_,i)=>({...plan.courses[1],id:`c${i}`,code:`DEMO ${i}`,scheduledTermId:i===0?'missing':null}));
    assert.equal(checkPlan(plan,id).issues.length,6);
    assert.match(checkPlan(plan,id).scheduleSummary,/6 planned/);
});
test('credit caps include zero and exact limits', () => {
    const plan=fixture();plan.terms[0].creditCap=3;assert.equal(checkPlan(plan,id).status,'PASS');
    plan.terms[0].creditCap=0;assert.equal(checkPlan(plan,id).issues.filter(i=>i.kind==='SCHEDULE').length,1);
});
test('basic checks describe their actual scope even when the plan is empty', () => {
    const plan=fixture();plan.courses=[];
    const result=checkPlan(plan,id);
    assert.match(result.requirementSummary,/not checked/);assert.match(result.scheduleSummary,/not checked/);
    assert.doesNotMatch(JSON.stringify(result),/on pace|No schedule conflicts|Mock credit/);
});

test('adding a term after deleting an earlier term preserves prerequisite chronology', () => {
    const plan=fixture();plan.terms=plan.terms.filter(t=>t.id!=='fall');
    plan.courses[1].scheduledTermId='spring';
    const later={id:'later',name:'Later',order:nextTermOrder(plan.terms),courseIds:['c3']};
    plan.terms.push(later);plan.courses[2].scheduledTermId='later';plan.courses[2].prereqs=['DEMO 201'];
    assert.equal(later.order,3);
    assert.equal(hasPrerequisiteViolation(plan.courses[2],plan,'later'),false);
});
test('an empty schedule starts its term ordering at one', () => {
    assert.equal(nextTermOrder([]),1);
});
