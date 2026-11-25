import csv,sys
def load(p):
    d={}
    for r in csv.DictReader(open(p)):
        d[r['PACKAGE'].split('.')[-1]+'.'+r['CLASS']]=r
    return d
def tot(d,k): return sum(int(r[k]) for r in d.values())
def pct(d,a):
    c=tot(d,a+'_COVERED'); m=tot(d,a+'_MISSED'); return 100*c/(c+m), c, c+m
b,a=load(sys.argv[1]),load(sys.argv[2])
for kind in ['INSTRUCTION','LINE','BRANCH','METHOD']:
    pb,cb,nb=pct(b,kind); pa,ca,na=pct(a,kind)
    print(f"{kind:12s} before {pb:5.1f}% ({cb}/{nb})  after {pa:5.1f}% ({ca}/{na})  delta {pa-pb:+.1f} pp")
print()
rows=[]
for k in a:
    lb=int(b[k]['LINE_COVERED']) if k in b else 0
    la=int(a[k]['LINE_COVERED']); n=la+int(a[k]['LINE_MISSED'])
    nbt=(lb+int(b[k]['LINE_MISSED'])) if k in b else n
    if la!=lb: rows.append((la-lb,k,lb,la,n,nbt))
for d,k,lb,la,n,nbt in sorted(rows,reverse=True):
    print(f"{k:55s} lines {lb:3d}/{nbt:<3d} -> {la:3d}/{n:<3d}  ({100*lb/max(nbt,1):5.1f}% -> {100*la/max(n,1):5.1f}%)")
