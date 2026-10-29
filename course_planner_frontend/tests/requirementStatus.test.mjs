import test from 'node:test';
import assert from 'node:assert/strict';
import { getCourseGroups, getNodeStatus, getSectionSummary } from '../src/pages/plan/requirements/requirementStatus.ts';
const options = ['DEMO 101','DEMO 201','DEMO 301'].map(code=>({code,title:code,credits:3}));
const node = (overrides={})=>({id:1,name:'Choose two',kind:'N_OF',nRequired:2,courseOptions:options,attributeOptions:[],children:[],...overrides});
test('flat N_OF options remain independent choices rather than one required bundle',()=>{
    const n=node();const groups=getCourseGroups(n);
    assert.equal(groups.length,3);assert.ok(groups.every(g=>g.kind==='single'));
    assert.equal(getNodeStatus(n,new Set(['DEMO 101'])).status,'IN_PROGRESS');
    assert.equal(getNodeStatus(n,new Set(['DEMO 101','DEMO 201'])).status,'DONE');
});
test('flat ANY_OF options allow a single matching course',()=>{
    const n=node({kind:'ANY_OF',nRequired:1});
    assert.equal(getNodeStatus(n,new Set(['DEMO 201'])).status,'DONE');
});
test('ALL_OF bundles still require every listed course',()=>{
    const n=node({kind:'ALL_OF',name:'Core',nRequired:null});
    assert.equal(getNodeStatus(n,new Set(['DEMO 101'])).status,'IN_PROGRESS');
    assert.equal(getNodeStatus(n,new Set(options.map(c=>c.code))).status,'DONE');
});

test('informational policy notes are excluded from course coverage totals',()=>{
    const n=node({kind:'ALL_OF',courseOptions:[],children:[node(),node({id:2,kind:'GRADE_MIN',courseOptions:[],gradeMin:'C'})]});
    const result=getSectionSummary(n,new Set(['DEMO 101','DEMO 201']));
    assert.equal(result.totalCount,1);assert.equal(result.satisfiedCount,1);
});

test('a credit minimum does not override an explicit course count',()=>{
    const n=node({nRequired:2,creditMin:6,courseOptions:[{code:'DEMO 101',credits:6},{code:'DEMO 201',credits:3}]});
    assert.equal(getNodeStatus(n,new Set(['DEMO 101'])).status,'IN_PROGRESS');
    assert.equal(getNodeStatus(n,new Set(['DEMO 101','DEMO 201'])).status,'DONE');
});
test('unknown credits cannot satisfy a credit minimum just by matching course count',()=>{
    const n=node({nRequired:2,creditMin:6,courseOptions:[{code:'DEMO 101',credits:null},{code:'DEMO 201',credits:3}]});
    assert.equal(getNodeStatus(n,new Set(['DEMO 101','DEMO 201'])).status,'IN_PROGRESS');
});

test('zero-credit selections do not bypass a positive credit minimum',()=>{
    const n=node({nRequired:2,creditMin:6,courseOptions:[{code:'DEMO 101',credits:0},{code:'DEMO 201',credits:0}]});
    assert.equal(getNodeStatus(n,new Set(['DEMO 101','DEMO 201'])).status,'IN_PROGRESS');
});
test('ALL_OF requirements retain their course count when a credit minimum is met',()=>{
    const n=node({kind:'ALL_OF',name:'Core',nRequired:null,creditMin:6,courseOptions:[{code:'DEMO 101',credits:6},{code:'DEMO 201',credits:3}]});
    assert.equal(getNodeStatus(n,new Set(['DEMO 101'])).status,'IN_PROGRESS');
});
