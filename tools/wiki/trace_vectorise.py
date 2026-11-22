import numpy as np, json, sys
from PIL import Image, ImageFilter
X0,X1,Y0,Y1=215,1690,335,605
im=Image.open('ref.jpg').convert('RGB').crop((X0,Y0,X1,Y1))
im=im.filter(ImageFilter.GaussianBlur(0.8))
a=np.asarray(im).astype(float)
L=a.mean(2); S=a.max(2)-a.min(2); BR=(a[:,:,0]-a[:,:,2])
def trace(mask):
    """boundary loops of binary mask -> list of point lists (pixel-corner coords)"""
    h,w=mask.shape
    m=np.pad(mask,1,mode='constant')
    edges={}
    # directed edges with filled on the left
    ys,xs=np.nonzero(m[1:-1,1:-1])
    def add(p,q): edges.setdefault(p,[]).append(q)
    for y,x in zip(ys,xs):
        Y=y+1;Xx=x+1
        if not m[Y-1,Xx]: add((x+1,y),(x,y))      # top edge, going left
        if not m[Y+1,Xx]: add((x,y+1),(x+1,y+1))  # bottom edge, going right
        if not m[Y,Xx-1]: add((x,y),(x,y+1))      # left edge, going down
        if not m[Y,Xx+1]: add((x+1,y+1),(x+1,y))  # right edge, going up
    loops=[]
    while edges:
        p=next(iter(edges)); start=p; loop=[p]
        while True:
            nx=edges[p]; q=nx.pop()
            if not nx: del edges[p]
            p=q
            if p==start: break
            loop.append(p)
            if p not in edges: break
        loops.append(loop)
    return loops
def rdp(pts,eps):
    pts=np.array(pts,float)
    if len(pts)<4: return pts
    keep=np.zeros(len(pts),bool); keep[0]=keep[-1]=True
    st=[(0,len(pts)-1)]
    while st:
        i,j=st.pop()
        if j<=i+1: continue
        p,q=pts[i],pts[j]; d=q-p; n=np.hypot(*d)
        seg=pts[i+1:j]
        dist=np.abs(d[0]*(seg[:,1]-p[1])-d[1]*(seg[:,0]-p[0]))/n if n>0 else np.hypot(*(seg-p).T)
        k=dist.argmax()
        if dist[k]>eps: keep[i+1+k]=True; st+=[(i,i+1+k),(i+1+k,j)]
    return pts[keep]
def area(p):
    x,y=p[:,0],p[:,1]; return 0.5*(np.dot(x,np.roll(y,-1))-np.dot(y,np.roll(x,-1)))
def vec(mask,eps=1.3,minA=40,minHole=40):
    out=[]
    for lp in trace(mask):
        if len(lp)<8: continue
        # drop collinear points first cheaply by rdp on closed loop: split at farthest
        p=np.array(lp,float)
        i=np.argmax(np.hypot(*(p-p[0]).T))
        r1=rdp(np.vstack([p[:i+1]]),eps); r2=rdp(np.vstack([p[i:],p[:1]]),eps)
        q=np.vstack([r1[:-1],r2[:-1]])
        A=abs(area(q))
        if A<(minA if area(q)<0 else minHole): continue
        out.append(q+[X0,Y0])
    return out
sil=(S<32)&(L<236)&(BR<12)
face=(S<32)&(L>=125)&(L<241)
band=(S<32)&(L>=38)&(L<125)&(BR<12)
ink=(S<32)&(L<38)
# face with cracks filled: close small holes via area filter later, cracks = darker blobs inside face hull
from PIL import ImageDraw
def raster(polys):
    m=np.zeros(L.shape,bool)
    for p in polys:
        im2=Image.new('1',(L.shape[1],L.shape[0]),0); ImageDraw.Draw(im2).polygon([(x-X0,y-Y0) for x,y in p],fill=1)
        m^=np.asarray(im2)
    return m
from PIL import ImageFilter as F0
def dil0(m,n): return np.asarray(Image.fromarray((m*255).astype('uint8')).filter(F0.MaxFilter(n)))>0
sil=sil&dil0(face|band,23)
res={}
def ero0(m,n): return np.asarray(Image.fromarray((m*255).astype('uint8')).filter(F0.MinFilter(n)))>0
closed=ero0(dil0(face|band,15),15)
res['sil']=vec(dil0(closed,21),1.6,200,1e9)
res['face']=vec(face,1.2,60,500)   # holes <500 dropped (cracks)
res['band']=vec(band&~face,1.2,60,60)
json.dump({k:[p.round(1).tolist() for p in v] for k,v in res.items()},open('vec.json','w'))
print({k:len(v) for k,v in res.items()})

ff=raster(res['face'])
from PIL import ImageFilter as F
def dil(m,n): 
    return np.asarray(Image.fromarray((m*255).astype('uint8')).filter(F.MaxFilter(n)))>0
def ero(m,n): 
    return np.asarray(Image.fromarray((m*255).astype('uint8')).filter(F.MinFilter(n)))>0
inner=ero(ff,5)
rowm=np.array([L[y][inner[y]].mean() if inner[y].sum()>20 else 200 for y in range(L.shape[0])])
loc=np.asarray(Image.fromarray(np.where(inner,L,200).astype('uint8')).filter(F.GaussianBlur(14)))
res['crack']=vec(inner&(L<loc-30)&(L>60),0.9,12,12)
res['fleck']=vec(inner&(L>238),0.8,6,6)
json.dump({k:[p.round(1).tolist() for p in v] for k,v in res.items()},open('vec.json','w'))
print({k:len(v) for k,v in res.items()})
