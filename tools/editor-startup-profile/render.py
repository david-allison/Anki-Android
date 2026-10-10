# SPDX-License-Identifier: GPL-3.0-or-later
"""Build an offline flame graph from captured stacks, preserving clock labels."""

from pathlib import Path
from collections import Counter
import json, sys

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent
DATA = []
SUMMARY = {}
FRAMES = []
FRAME_IDS = {}
SPEED = []


def add(name, weights, note):
    rows = [[list(s), w] for s, w in weights if w > 0]
    total = sum(w for _, w in rows)
    DATA.append({"name": name, "rows": rows, "note": note, "total": total})
    self_time = Counter()
    inclusive = Counter()
    for stack, weight in rows:
        self_time[stack[-1]] += weight
        for f in set(stack):
            inclusive[f] += weight
    SUMMARY[name] = {
        "totalUs": total,
        "topSelf": self_time.most_common(30),
        "topInclusive": inclusive.most_common(50),
    }
    samples = []
    for stack, _ in rows:
        ids = []
        for frame in stack:
            if frame not in FRAME_IDS:
                FRAME_IDS[frame] = len(FRAMES)
                FRAMES.append({"name": frame})
            ids.append(FRAME_IDS[frame])
        samples.append(ids)
    SPEED.append(
        {
            "type": "sampled",
            "name": name,
            "unit": "microseconds",
            "startValue": 0,
            "endValue": total,
            "samples": samples,
            "weights": [w for _, w in rows],
        }
    )


for label in ["native-cold", "native-warm"]:
    d = json.loads((ROOT / (label + ".stacks.json")).read_text())
    for t in d["threads"]:
        if t["thread"] != "main" and t["cpuUs"] < 3000:
            continue
        note = "ART 1 ms sampled stacks, weighted by thread CPU clock deltas. Profiling substantially inflates work in this debug build. Includes app startup and keyboard settling; native callees are opaque. Aggregated, not chronological."
        add(f"{label} / {t['thread']} [{t['tid']}] / CPU", t["cpuStacks"], note)
        if t["thread"] == "main":
            add(
                f"{label} / main / elapsed including waits",
                t["wallStacks"],
                "ART sampled stacks weighted by wall-clock deltas. Includes waiting and idle polling: this is NOT CPU time. No tail is extrapolated after the final event.",
            )

for label in ["v8-cold", "v8-warm"]:
    p = json.loads((ROOT / (label + ".cpuprofile")).read_text())
    symbols = json.loads((ROOT / (label + ".symbols.json")).read_text())
    nodes = {n["id"]: n for n in p["nodes"]}
    parents = {c: n["id"] for n in p["nodes"] for c in n.get("children", [])}
    weights = Counter()
    all_weights = Counter()
    files = Counter()
    for sample, weight in zip(p["samples"], p["timeDeltas"]):
        stack = []
        n = sample
        while n is not None:
            frame = nodes[n]["callFrame"]
            symbol = symbols.get(str(n))
            name = frame["functionName"] or "(anonymous)"
            if symbol:
                name = f"{symbol['name'] or name} — {symbol['source']}:{symbol['line']}:{symbol['column']+1}"
            elif frame["url"]:
                name += f" — {frame['url'].replace('https://appassets.androidplatform.net/','')}:{frame['lineNumber']+1}:{frame['columnNumber']+1}"
            stack.append(name)
            n = parents.get(n)
        stack = tuple(reversed(stack))
        all_weights[stack] += weight
        if nodes[sample]["callFrame"]["functionName"] not in ["(idle)", "(program)"]:
            weights[stack] += weight
            files[
                symbols.get(str(sample), {}).get(
                    "source",
                    nodes[sample]["callFrame"]["url"]
                    or nodes[sample]["callFrame"]["functionName"],
                )
            ] += weight
    note = "V8 500 µs requested sampling; weights are sampled elapsed intervals, NOT OS CPU measurements. A 3 s diagnostic navigation pause allows attachment before JS bootstrap; these runs are NOT cold-start benchmarks. Includes field construction and focus. Source maps match bundle bytes exactly."
    add(label + " / JavaScript + GC (idle/program excluded)", weights.items(), note)
    add(
        label + " / all V8 samples",
        all_weights.items(),
        note
        + " (program) is unattributed execution; do not assume it is JavaScript or idle.",
    )
    SUMMARY[label + " self by source"] = files.most_common()

(ROOT / "summary.json").write_text(json.dumps(SUMMARY, indent=2))
(ROOT / "flames.speedscope.json").write_text(
    json.dumps(
        {
            "$schema": "https://www.speedscope.app/file-format-schema.json",
            "shared": {"frames": FRAMES},
            "profiles": SPEED,
            "activeProfileIndex": 0,
            "name": "Compose editor local profiles",
            "exporter": "editor-startup-profile",
        }
    )
)
HTML = """<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Compose editor — startup flame graphs</title>
<style>
body{font:15px system-ui,sans-serif;color:#17253a;margin:24px;background:#f7f9fc}h1{font-size:25px;margin-bottom:8px}p{max-width:1100px;line-height:1.5}select,input,button{font:inherit;padding:7px;border:1px solid #a8b5c8;border-radius:5px;background:white}select{max-width:65vw}#chart{background:white;border:1px solid #c8d3e2;overflow:auto;margin-top:14px}svg{display:block}text{font:11px ui-monospace,monospace;pointer-events:none}rect{cursor:pointer;stroke:white;stroke-width:.5}#hover{padding:10px;background:#e6edf8;min-height:42px;overflow-wrap:anywhere}table{border-collapse:collapse;background:white;width:100%;font-size:13px}td,th{padding:6px;text-align:left;border-bottom:1px solid #dce3ec}td:last-child{overflow-wrap:anywhere}small{color:#486079}.controls{display:flex;gap:8px;flex-wrap:wrap}.callout{border-left:4px solid #496bd6;padding-left:12px}
</style>
<h1>Compose editor: where startup work goes</h1>
<p class="callout"><b>Clean baseline remains 681 ms cold / 249 ms repeat.</b> These profiles locate work; their durations are not benchmark results. Pixel 9 Pro, isolated debug APK. Native and web captures are separate runs. C++/Rust/GPU stacks are not covered.</p>
<div class="controls"><select id="pick" aria-label="Profile"></select><input id="search" placeholder="Find a function or source file" aria-label="Search frames"><button id="reset">Reset zoom</button><button id="export">Save SVG</button></div>
<p id="note"></p><small id="total"></small><div id="chart"></div><div id="hover">Click a rectangle to zoom. Search highlights matching frames. Width is the selected weight; vertical position is stack depth.</div>
<h2>Largest self weights</h2><p><small>Self weight excludes child frames. Narrow bars are sampling noise; one run per profile is insufficient to rank tiny differences.</small></p><table><thead><tr><th>Weight (ms)</th><th>Share</th><th>Frame</th></tr></thead><tbody id="self"></tbody></table>
<script id="data" type="application/json">DATA_TOKEN</script>
<script>
const payload=JSON.parse(document.getElementById('data').textContent),data=payload.profiles,pick=document.getElementById('pick'),ns='http://www.w3.org/2000/svg';let root,view,current;
for(let i=0;i<data.length;i++){const o=document.createElement('option');o.value=i;o.textContent=data[i].name;pick.append(o)}
function el(tag,attrs){const e=document.createElementNS(ns,tag);for(const [k,v]of Object.entries(attrs))e.setAttribute(k,v);return e}
function tree(rows){const root={name:'all captured weight',value:0,children:new Map()};for(const [s,w]of rows){root.value+=w;let n=root;for(const f of s){if(!n.children.has(f))n.children.set(f,{name:f,value:0,children:new Map()});n=n.children.get(f);n.value+=w}}return root}
function color(s){let h=0;for(const c of s)h=(h*31+c.charCodeAt(0))|0;return `hsl(${Math.abs(h)%70+10} 78% 73%)`}
function render(){const width=Math.max(900,document.getElementById('chart').clientWidth),svg=el('svg',{xmlns:ns,width,role:'img','aria-label':current.name});const style=el('style',{});style.textContent='text{font:11px monospace;pointer-events:none}rect{stroke:white;stroke-width:.5}';svg.append(style);let maxDepth=0;const query=document.getElementById('search').value.toLowerCase();
function draw(n,x,w,depth){if(w<.6)return;maxDepth=Math.max(maxDepth,depth);const g=el('g',{}),r=el('rect',{x,y:depth*22,width:w,height:21,fill:query&&n.name.toLowerCase().includes(query)?'#d391ff':color(n.name)}),title=el('title',{});title.textContent=n.name+' | '+(n.value/1000).toFixed(3)+' ms | '+(100*n.value/current.total).toFixed(2)+'% of whole profile';g.append(r,title);if(w>30){const t=el('text',{x:x+3,y:depth*22+15});t.textContent=n.name.slice(0,Math.max(0,Math.floor((w-7)/6.7)));g.append(t)}g.onclick=()=>{view=n;render()};g.onmouseenter=()=>document.getElementById('hover').textContent=title.textContent;svg.append(g);let cx=x;for(const child of [...n.children.values()].sort((a,b)=>b.value-a.value)){const cw=w*child.value/n.value;draw(child,cx,cw,depth+1);cx+=cw}}
draw(view,0,width,0);svg.setAttribute('height',(maxDepth+1)*22);document.getElementById('chart').replaceChildren(svg)}
function select(){current={...data[+pick.value],rows:data[+pick.value].rows.map(([s,w])=>[s.map(i=>payload.frames[i].name),w])};root=tree(current.rows);view=root;document.getElementById('note').textContent=current.note;document.getElementById('total').textContent='Total selected weight: '+(current.total/1000).toFixed(2)+' ms. Inclusive frames overlap; do not add them.';const counts=new Map();for(const [s,w]of current.rows)counts.set(s.at(-1),(counts.get(s.at(-1))||0)+w);const body=document.getElementById('self');body.replaceChildren();for(const [f,w]of [...counts].sort((a,b)=>b[1]-a[1]).slice(0,25)){const tr=document.createElement('tr');for(const v of [(w/1000).toFixed(2),(100*w/current.total).toFixed(2)+'%',f]){const td=document.createElement('td');td.textContent=v;tr.append(td)}body.append(tr)}render()}
pick.onchange=select;document.getElementById('reset').onclick=()=>{view=root;render()};document.getElementById('search').oninput=render;document.getElementById('export').onclick=()=>{const blob=new Blob([new XMLSerializer().serializeToString(document.querySelector('svg'))],{type:'image/svg+xml'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='editor-flame-graph.svg';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)};window.onresize=render;select();
</script>"""
(ROOT / "flamegraph.html").write_text(
    HTML.replace(
        "DATA_TOKEN",
        json.dumps(
            {
                "frames": FRAMES,
                "profiles": [
                    {
                        **d,
                        "rows": [[[FRAME_IDS[f] for f in s], w] for s, w in d["rows"]],
                    }
                    for d in DATA
                ],
            },
            separators=(",", ":"),
        ).replace("<", "\\u003c"),
    )
)
print("Rendered", len(DATA), "profiles;", len(FRAMES), "frames")
