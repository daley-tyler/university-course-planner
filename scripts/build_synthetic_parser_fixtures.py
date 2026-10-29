"""Author-owned parser examples, specified without reading catalog pages or parser output.

HTML and complete expected trees are declared together for review. This module
uses only the standard library and never invokes the Java parser or old fixtures.
"""

import argparse
from html import escape
import json
from pathlib import Path

DESTINATION = Path(__file__).resolve().parents[1] / "backend/src/test/resources/synthetic"


def node(name, kind="ALL_OF", children=(), **fields):
    # Jackson exposes Lombok getNRequired() as "nrequired" in this DTO.
    if "nRequired" in fields:
        fields["nrequired"] = fields.pop("nRequired")
    return dict(name=name, kind=kind, doubleCountAllowed=True,
                children=list(children), courseOptions=[], **fields)


def leaf(code, ref, **fields):
    subject, number = code.split()
    result = node(code, "LEAF_COURSES", sourceRef=f"#{ref}", **fields)
    result["courseOptions"] = [dict(subjectCode=subject, courseNumber=number)]
    return result


def course(code, ref, footnote=None, alternative=False):
    mark = f"<sup>{footnote}</sup>" if footnote else ""
    style = ' class="orclass"' if alternative else ""
    return f'<tr id="{ref}"{style}><td class="codecol"><a class="bubblelink code">{code}</a>{mark}</td></tr>'


def rule(text, ref, hours="", header=False, footnote=None):
    style = ' class="areaheader"' if header else ""
    mark = f"<sup>{footnote}</sup>" if footnote else ""
    return (f'<tr id="{ref}"{style}><td><span class="courselistcomment">{escape(text)}</span>{mark}</td>'
            f'<td class="hourscol">{hours}</td></tr>')


def table(rows, ref="table", title="Invented workshop"):
    return f'<table id="{ref}" class="sc_courselist"><caption>{title}</caption><tbody>{rows}</tbody></table>'


def definitions(text, number=1, ref="policy"):
    return f'<dl class="sc_footnotes"><dt>{number}</dt><dd id="{ref}">{escape(text)}</dd></dl>'


def table_node(children, ref="table", title="Invented workshop"):
    return node(title, children=children, sourceRef=f"#{ref}")


def default_section(children, ref="a"):
    return node("Required Courses", children=children, sourceRef=f"#{ref}")


def cases():
    result = []

    def add(slug, purpose, body, children):
        url = f"https://catalog.example.edu/synthetic/{slug}/"
        html = '<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Synthetic parser case</title></head><body><div id="textcontainer">' + body + '</div></body></html>\n'
        expected = node("Program Requirements", children=children, sourceUrl=url)
        result.append(dict(slug=slug, purpose=purpose, html=html, expected=expected))

    a, b, c, d = (leaf(f"SYN {n}", ref) for n, ref in [(113,"a"),(227,"b"),(339,"c"),(451,"d")])
    rows = course("SYN 113", "a") + course("SYN 227", "b")
    add("unheaded-courses", "Default section and complete ordered course membership", table(rows),
        [table_node([default_section([a,b])])])

    # Header and selector constraints are explicit expected values, not extracted.
    for slug, text, hours, kind, fields, purpose in [
        ("header-minimum", "Studio foundations", "7", "CREDIT_AT_LEAST", dict(creditMin=7), "Hours stored outside a header label"),
        ("header-range", "Exploration block", "5-11", "CREDIT_AT_LEAST", dict(creditMin=5,creditMax=11), "Both bounds of header hours"),
        ("header-count-and-hours", "Choose two courses", "6-9", "N_OF", dict(nRequired=2,creditMin=6,creditMax=9), "Simultaneous count and credit range"),
        ("header-single-choice", "Select one", "3", "ANY_OF", dict(nRequired=1,creditMin=3), "Single-choice header with credits"),
        ("header-parenthetical", "Workshop series (3 courses)", "", "N_OF", dict(nRequired=3), "Parenthetical selection count and clean label"),
    ]:
        name = "Workshop series" if slug=="header-parenthetical" else text
        add(slug,purpose,table(rule(text,"rule",hours,True)+rows),
            [table_node([node(name,kind,[a,b],sourceRef="#rule",**fields)])])

    for slug, text, hours, kind, fields, codes, purpose in [
        ("choose-three", "Choose three courses", "", "N_OF", dict(nRequired=3), [a,b,c], "Explicit comment count"),
        ("take-both", "Take both", "", "ALL_OF", {}, [a,b], "Required pair is not an alternative"),
        ("select-all", "Select all of the following", "", "ALL_OF", {}, [a,b,c], "All listed options remain required"),
        ("minimum-credit-comment", "Select a minimum of four credit hours", "", "CREDIT_AT_LEAST",dict(creditMin=4),[a,b],"Word-based credit minimum"),
        ("credit-range-comment", "Choose 7 to 11 credits", "", "CREDIT_AT_LEAST",dict(creditMin=7,creditMax=11),[a,b],"Selection credit interval"),
        ("maximum-credit-comment", "Select up to nine credits", "", "CREDIT_AT_LEAST",dict(creditMax=9),[a,b],"Upper bound without invented lower bound"),
        ("duration-not-count", "Complete four semesters in the practice studio", "5", "CREDIT_AT_LEAST",dict(creditMin=5),[a,b],"Duration must not become a course count"),
        ("these-four-courses", "Take these four courses", "12", "N_OF",dict(nRequired=4,creditMin=12),[a,b,c,d],"Explicit count retained with hours column"),
    ]:
        body = rule(text,"rule",hours) + ''.join(course(n['name'],n['sourceRef'][1:]) for n in codes)
        add(slug,purpose,table(body),[table_node([node(text,kind,codes,sourceRef="#rule",**fields)])])

    add("alternative-rows","Adjacent OR rows form alternatives, preserving both codes",
        table(course("SYN 113","a")+course("SYN 227","b",alternative=True)),
        [table_node([default_section([node("One of multiple options","ANY_OF",[a,b],sourceRef="#a")])])])
    pair = '<tr id="pair"><td class="codecol"><a class="bubblelink code">SYN 113</a> &amp; <a class="bubblelink code">SYN 227</a></td></tr>'
    add("paired-links","Two course links in a row must both be completed",table(pair),
        [table_node([default_section([node("SYN 113 & SYN 227",children=[leaf("SYN 113","pair"),leaf("SYN 227","pair")],sourceRef="#pair")],"pair")])])

    for slug,hours,fields in [("total-minimum","17",dict(creditMin=17)),("total-range","17-23",dict(creditMin=17,creditMax=23))]:
        total=f'<tr id="total"><td>Total Credit Hours</td><td class="hourscol">{hours}</td></tr>'
        add(slug,"Total constraint coexists with course membership",table(rows+total),
            [table_node([default_section([a,b]),node("Total Credit Hours","CREDIT_AT_LEAST",sourceRef="#total",**fields)])])

    add("level-minimum","Level filter and credits are retained together",table(rule("300-level studio","rule","8",True)+rows),
        [table_node([node("300-level studio","LEVEL_AT_LEAST",[a,b],sourceRef="#rule",levelMin=300,creditMin=8)])])
    for slug,text,fields in [("level-hours-cap","No more than 7 hours at 200 level",dict(levelCap200=7)),("level-course-cap","No more than two courses at 300 level",dict(levelCap300Courses=2)),("group-course-cap","At most two courses from the amber set",dict(nRequired=2))]:
        # A cap is a constraint, not a container that absorbs following rows.
        add(slug,"Caps remain constraints alongside required courses",table(rule(text,"rule")+rows),
            [table_node([node(text,"COURSE_CAP",sourceRef="#rule",**fields),default_section([a,b])])])

    policy="The invented rover review uses a separate assessment."
    add("attached-footnote","Referenced policy appears on its course once",table(course("SYN 113","a",7))+definitions(policy,7),
        [table_node([default_section([leaf("SYN 113","a",footnoteText=policy)])])])
    p1,p2="Amber studio uses the spring showcase.","Cobalt studio uses the autumn showcase."
    body=table(course("SYN 113","a",1),"amber","Amber studio")+definitions(p1,1,"amber-policy")+table(course("SYN 227","b",1),"cobalt","Cobalt studio")+definitions(p2,1,"cobalt-policy")
    add("scoped-footnote-numbers","Restarted numbering does not overwrite another section",body,
        [table_node([default_section([leaf("SYN 113","a",footnoteText=p1)])],"amber","Amber studio"),table_node([default_section([leaf("SYN 227","b",footnoteText=p2)],"b")],"cobalt","Cobalt studio")])
    p="The invented mural review takes place in the north studio."
    add("unreferenced-policy","Unreferenced policy is preserved rather than lost",table(rows)+definitions(p,4),
        [table_node([default_section([a,b])]),node("Footnote","NOTE_ONLY",notes=p,footnoteText=p,sourceUrl="https://catalog.example.edu/synthetic/unreferenced-policy/",sourceRef="#policy")])
    p="The invented telescope review needs an advisor discussion."
    body=table(rule("Choose two courses from the distant list","empty",footnote=9)+rule("Rover core","core",header=True)+course("SYN 113","a"))+definitions(p,9)
    add("pruned-selector-policy","Policy survives pruning an empty selector",body,
        [table_node([node("Rover core",children=[a],sourceRef="#core")]),node("Footnote","NOTE_ONLY",notes=p,footnoteText=p,sourceUrl="https://catalog.example.edu/synthetic/pruned-selector-policy/",sourceRef="#policy")])
    p="Studio credits cannot be double-counted."
    add("header-footnote","Header policy retains the double-counting flag",table(rule("Rover studio","rule",header=True,footnote=3)+rows)+definitions(p,3),
        [table_node([dict(node("Rover studio",children=[a,b],sourceRef="#rule",footnoteText=p),doubleCountAllowed=False)])])
    p="Only five credits may count toward both the rover and mural requirements."
    add("narrative-overlap-cap","Narrative policy remains visible with its overlap cap",'<p id="narrative">'+p+'</p>'+table(rows),
        [table_node([default_section([a,b])]),node(p,"NOTE_ONLY",notes=p,sourceRef="#narrative",sourceUrl="https://catalog.example.edu/synthetic/narrative-overlap-cap/",doubleCountCap=5)])
    body=course("SYN 113","a")+rule("or","or")+course("SYN 227","b")+rule("and","and")+course("SYN 339","c")
    expr=node("One of multiple options","ANY_OF",[a,node("All of the following",children=[b,c])])
    add("connector-precedence","AND binds within an OR option; no course is lost",table(body),[table_node([default_section([expr])])])
    body=rule("Tracks","tracks",header=True)+rule("A: Rover track","track-a",header=True)+course("SYN 113","a")+rule("B: Mural track","track-b",header=True)+course("SYN 227","b")
    tracks=node("Tracks","ANY_OF",[node("A: Rover track","TRACK_OPTION",[a],notes="A: Rover track",sourceRef="#track-a"),node("B: Mural track","TRACK_OPTION",[b],notes="B: Mural track",sourceRef="#track-b")],nRequired=1,sourceRef="#tracks")
    add("track-options","Independent track alternatives preserve their own children",table(body),[table_node([tracks])])
    assert len(result)==30
    return result


def render():
    outputs={}
    entries=[]
    for case in cases():
        slug=case['slug']
        outputs[f'{slug}.html']=case['html']
        outputs[f'{slug}.expected.json']=json.dumps(case['expected'],indent=2)+'\n'
        entries.append(dict(slug=slug,purpose=case['purpose']))
    outputs['cases.json']=json.dumps(entries,indent=2)+'\n'
    return outputs


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true',help='Verify committed examples without changing them')
    args=parser.parse_args()
    outputs=render()
    if args.check:
        actual={p.name for p in DESTINATION.iterdir() if p.is_file()}
        assert actual==set(outputs), 'Unexpected or missing synthetic fixture files'
        for name,text in outputs.items():
            assert (DESTINATION/name).read_text()==text, f'Synthetic specification drift: {name}'
        print('30 independently specified HTML/JSON pairs match their source specification.')
    else:
        DESTINATION.mkdir(parents=True,exist_ok=True)
        for name,text in outputs.items():
            (DESTINATION/name).write_text(text)


if __name__=='__main__':
    main()
