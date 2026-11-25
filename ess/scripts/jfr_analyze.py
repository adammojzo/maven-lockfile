import json, sys, subprocess, collections, re
jfrfile = sys.argv[1]
def events(name, depth=96):
    out = subprocess.run(['jfr','print','--json','--stack-depth',str(depth),'--events',name,jfrfile],capture_output=True,text=True).stdout
    return json.loads(out)['recording']['events']
def dur(e):
    d=e['values'].get('duration','PT0S')
    m=re.match(r'PT(?:(\d+)M)?([\d.]+)S',d)
    return (int(m.group(1) or 0)*60+float(m.group(2))) if m else 0.0
def frames(e):
    st=e['values'].get('stackTrace')
    if not st: return []
    return [re.sub(r'\$\$Lambda.*','$$Lambda',f['method']['type']['name'].replace('/','.'))+'.'+f['method']['name'] for f in st['frames']]
OURS=('io.github.chains_project','org.apache.maven.shared.dependency.graph.internal.Spying')
def first_ours(fr):
    for f in fr:
        if f.startswith(OURS): return f
    return '(no plugin frame)'
def plugin_path(fr, n=4):
    ours=[f.replace('io.github.chains_project.maven_lockfile.','') for f in fr if f.startswith(OURS)]
    return ' <- '.join(ours[:n]) if ours else '(no plugin frame)'

# 1) Network: socket reads
sr=events('jdk.SocketRead')
tot=sum(dur(e) for e in sr)
print(f"== jdk.SocketRead (>=10ms): {len(sr)} events, total {tot:.1f} s blocked in socket reads")
hosts=collections.Counter(); hostt=collections.Counter()
for e in sr:
    h=e['values'].get('host') or e['values'].get('address'); hosts[h]+=1; hostt[h]+=dur(e)
for h,c in hosts.most_common(5): print(f"   host {h}: {c} reads, {hostt[h]:.1f} s")
by=collections.Counter(); byc=collections.Counter()
for e in sr:
    k=plugin_path(frames(e)); by[k]+=dur(e); byc[k]+=1
for k,v in by.most_common(8): print(f"   {v:7.1f} s  {byc[k]:5d}x  {k}")

# 2) CPU: execution samples, attribute to first plugin frame (inclusive)
ex=events('jdk.ExecutionSample')
print(f"\n== jdk.ExecutionSample: {len(ex)} CPU samples")
incl=collections.Counter(); top=collections.Counter()
for e in ex:
    fr=frames(e)
    if fr: top[fr[0]]+=1
    seen=set()
    for f in fr:
        if f.startswith(OURS) and f not in seen:
            seen.add(f); incl[f]+=1
print("   top self frames:")
for k,v in top.most_common(10): print(f"   {100*v/len(ex):5.1f}%  {k}")
print("   plugin methods (inclusive):")
for k,v in incl.most_common(12): print(f"   {100*v/len(ex):5.1f}%  {k.replace('io.github.chains_project.maven_lockfile.','')}")
