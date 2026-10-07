import math
from PIL import Image, ImageDraw, ImageFont
TORSO,NECK,HR,UA,FA,TH,SH,FOOT=30,3,5.5,16,14,24,23,7
SHW,HIPW=8.5,5.0   # půlšířka ramen/boků v čelním pohledu
def d(a): r=math.radians(a); return (math.cos(r),math.sin(r))
def add(a,b):return(a[0]+b[0],a[1]+b[1])
def sub(a,b):return(a[0]-b[0],a[1]-b[1])
def mul(a,k):return(a[0]*k,a[1]*k)
def ln(a):return math.hypot(*a)
def ik(root,t,a,b,bend):
    dd=sub(t,root);dist=min(max(ln(dd),abs(a-b)+.01),a+b-.01)
    base=math.atan2(dd[1],dd[0]);c=max(-1,min(1,(a*a+dist*dist-b*b)/(2*a*dist)))
    ang=base+bend*math.acos(c);return add(root,(math.cos(ang)*a,math.sin(ang)*a))
def chain(root,target,a,b,bend):
    m=ik(root,target,a,b,bend);v=sub(target,m);e=add(m,mul(v,b/max(ln(v),.001)));return m,e
def mirror(p,cx):return(2*cx-p[0],p[1])
def solve(p):
    t=d(p['torso']);P=p['pelvis'];S=add(P,mul(t,TORSO+p.get('sh',0)));H=add(P,mul(t,TORSO+NECK+HR))
    a1=p.get('as',1);a2=p.get('as2',a1);ts=p.get('ts',1)
    J=dict(pelvis=P,shoulder=S,head=H,front=(t[1],-t[0]),view=p.get('v','S'))
    fa=p.get('fa',0)
    if J['view']=='S':
        J['elbow'],J['wrist']=chain(S,p['wrist'],UA*a1,FA*a1,p.get('eb',-1))
        J['knee'],J['ankle']=chain(P,p['ankle'],TH*ts,SH,p.get('kb',1))
        J['elbow2'],J['wrist2']=chain(S,p.get('wrist2',p['wrist']),UA*a2,FA*a2,p.get('eb2',p.get('eb',-1)))
        J['knee2'],J['ankle2']=chain(P,p.get('ankle2',p['ankle']),TH*ts,SH,p.get('kb2',p.get('kb',1)))
        J['toe']=add(J['ankle'],mul(d(fa),FOOT));J['toe2']=add(J['ankle2'],mul(d(p.get('fa2',fa)),FOOT))
        J['sR']=J['sL']=S;J['hR']=J['hL']=P
    else:
        n=(t[1],-t[0])  # vpravo od diváka
        sR=add(S,mul(n,SHW));sL=sub(S,mul(n,SHW));hR=add(P,mul(n,HIPW));hL=sub(P,mul(n,HIPW))
        J.update(sR=sR,sL=sL,hR=hR,hL=hL)
        cx=P[0];eb=p.get('eb',-1);kb=p.get('kb',1)
        J['elbow'],J['wrist']=chain(sR,p['wrist'],UA*a1,FA*a1,eb)
        J['elbow2'],J['wrist2']=chain(sL,p.get('wrist2',mirror(p['wrist'],cx)),UA*a2,FA*a2,p.get('eb2',-eb))
        J['knee'],J['ankle']=chain(hR,p['ankle'],TH*ts,SH,kb)
        J['knee2'],J['ankle2']=chain(hL,p.get('ankle2',mirror(p['ankle'],cx)),TH*ts,SH,p.get('kb2',-kb))
        J['toe']=add(J['ankle'],(2.5,-1));J['toe2']=add(J['ankle2'],(-2.5,-1))
    return J
INK={'F':'#283618','S':'#9EA294','C':'#606C38','P':'#283618','K':'#DCD3B4'}
OUT='#283618';SK='#DCD3B4';FAR='#B9AF8F';PRI='#BC6C25';SEC='#E9B072'
def joint(J,name):
    return {'K2':J['knee2'],'sR':J['sR'],'sL':J['sL'],'W':J['wrist'],'W2':J['wrist2'],'E':J['elbow'],'S':J['shoulder'],'P':J['pelvis'],'K':J['knee'],'K2':J['knee2'],'A':J['ankle'],'A2':J['ankle2'],'H':J['head']}[name]
def prop_pts(pr,Js):
    k=pr[0]
    if k=='only':return prop_pts(pr[2],Js)
    if k=='bar':return [pr[1],pr[2]]
    if k=='disc':return [add(pr[1],(-pr[2],-pr[2])),add(pr[1],(pr[2],pr[2]))]
    if k=='to':return [pr[1]]
    if k=='at':return sum([[add(joint(J,pr[1]),(-pr[2],-pr[2])),add(joint(J,pr[1]),(pr[2],pr[2]))] for J in Js],[])
    if k=='pad':return sum([[add(joint(J,pr[1]),pr[2]),add(joint(J,pr[1]),pr[3])] for J in Js],[])
    return []
def render(ill,prim,sec,fn=None,W=900,Hh=420,title=None):
    Js=[solve(ill['start'])]+([solve(ill['end'])] if ill.get('end') else [])
    pts=[]
    for J in Js:
        pts+=[J[k] for k in ('pelvis','shoulder','elbow','wrist','knee','ankle','toe','elbow2','wrist2','knee2','ankle2','toe2','sR','sL','hR','hL')]+[add(J['head'],(-7,-7)),add(J['head'],(7,7))]
    for pr in ill['props']:pts+=prop_pts(pr,Js)
    bx0=min(p[0] for p in pts)-3;bx1=max(p[0] for p in pts)+3;by0=min(p[1] for p in pts)-3;by1=max(p[1] for p in pts)+3
    img=Image.new('RGB',(W,Hh),'#F3EDD3');g=ImageDraw.Draw(img)
    lab=Hh*.12;gap=W*.04;fw=(W-gap)/2;s=min(fw/(bx1-bx0),(Hh-lab)/(by1-by0))
    single=len(Js)==1
    def hl(*ms):
        if any(m in prim for m in ms):return PRI
        if any(m in sec for m in ms):return SEC
    for i,J in enumerate(Js):
        left=i*(fw+gap)+(W/2-fw/2 if single else 0);ox=left+(fw-(bx1-bx0)*s)/2;oy=(Hh-lab-(by1-by0)*s)/2
        X=lambda p:(ox+(p[0]-bx0)*s, oy+(by1-p[1])*s)
        def line(p,q,w,c):
            g.line([X(p),X(q)],fill=c,width=max(1,int(round(w))))
            for e in (p,q):
                x,y=X(e);r=w/2;g.ellipse([x-r,y-r,x+r,y+r],fill=c)
        def circ(p,r,c):x,y=X(p);g.ellipse([x-r,y-r,x+r,y+r],fill=c)
        ol=1.1*s
        def limb(p,q,w,c,h=None):
            line(p,q,w*s+2*ol,OUT);line(p,q,w*s,c)
            if h:m1=add(p,mul(sub(q,p),.18));m2=add(p,mul(sub(q,p),.82));line(m1,m2,w*s*.72,h)
        def prop(pr,layer):
            if pr[0]=='only':
                if pr[1]==i: prop(pr[2],layer)
                return
            if pr[-1]!=layer:return
            k=pr[0]
            if k=='bar':line(pr[1],pr[2],pr[3]*s,INK[pr[4]])
            if k=='disc':circ(pr[1],pr[2]*s,INK[pr[3]])
            if k=='to':line(pr[1],joint(J,pr[2]),pr[3]*s,INK[pr[4]])
            if k=='pad':c=joint(J,pr[1]);line(add(c,pr[2]),add(c,pr[3]),pr[4]*s,INK[pr[5]])
            if k=='at':
                c=joint(J,pr[1]);circ(c,pr[2]*s,INK[pr[3]])
                if pr[3]=='P':x,y=X(c);r=pr[2]*.62*s;g.ellipse([x-r,y-r,x+r,y+r],outline=INK['S'],width=max(1,int(.8*s)))
        def head():
            limb(J['shoulder'],J['head'],4,SK);circ(J['head'],(HR+1.1)*s,OUT);circ(J['head'],HR*s,SK)
        def hand(w,c=SK):circ(w,3.4*s,OUT);circ(w,2.4*s,c)
        for pr in ill['props']:prop(pr,'B')
        if J['view']=='S':
            far=(-1.2,1.2)
            limb(add(J['pelvis'],far),add(J['knee2'],far),7.5,FAR);limb(add(J['knee2'],far),add(J['ankle2'],far),5.5,FAR);limb(add(J['ankle2'],far),add(J['toe2'],far),3,FAR)
            limb(add(J['shoulder'],far),add(J['elbow2'],far),5,FAR);limb(add(J['elbow2'],far),add(J['wrist2'],far),4.2,FAR);hand(add(J['wrist2'],far),FAR)
            def at(t,sd):return add(add(J['pelvis'],mul(sub(J['shoulder'],J['pelvis']),t)),mul(J['front'],sd*(6+1.8*t)))
            def poly(t0,t1,s0,s1):
                n=6;return [X(at(t0+(t1-t0)*k/n,s1)) for k in range(n+1)]+[X(at(t0+(t1-t0)*k/n,s0)) for k in range(n,-1,-1)]
            g.polygon(poly(-.08,1.04,-1,1),fill=SK,outline=OUT,width=max(1,int(2*ol)))
            for m,t0,t1,s0,s1 in [('CHEST',.58,.95,.05,.92),('ABS',.12,.55,.1,.85),('OBLIQUES',.1,.5,-.3,.4),('LATS',.38,.88,-.92,-.1),('TRAPS',.86,1.02,-.9,-.05),('LOWER_BACK',.05,.36,-.85,-.15),('GLUTES',-.06,.16,-.95,-.2)]:
                c=hl(m)
                if c:g.polygon(poly(t0,t1,s0,s1),fill=c)
            head()
            for pr in ill['props']:prop(pr,'M')
            limb(J['pelvis'],J['knee'],8,SK,hl('QUADS','HAMSTRINGS'));limb(J['knee'],J['ankle'],6,SK,hl('CALVES'));limb(J['ankle'],J['toe'],3.2,SK)
            limb(J['shoulder'],J['elbow'],5.6,SK,hl('TRICEPS','BICEPS'))
            c=hl('FRONT_DELTS','REAR_DELTS')
            if c:circ(J['shoulder'],3.6*s,c)
            limb(J['elbow'],J['wrist'],4.6,SK,hl('FOREARMS'));hand(J['wrist'])
        else:
            for kk,aa,tt,hh in (('knee','ankle','toe','hR'),('knee2','ankle2','toe2','hL')):
                limb(J[hh],J[kk],8,SK,hl('QUADS','HAMSTRINGS'));limb(J[kk],J[aa],6,SK,hl('CALVES'));limb(J[aa],J[tt],3.2,SK)
            P,S,n=J['pelvis'],J['shoulder'],sub(J['sR'],J['shoulder'])
            nn=mul(n,1/ln(n))
            def at(t,u):  # u -1..1 zleva doprava
                w=HIPW+1+(SHW+1-HIPW-1)*t
                return add(add(P,mul(sub(S,P),t)),mul(nn,u*w))
            def poly(t0,t1,u0,u1):
                n_=6;return [X(at(t0+(t1-t0)*k/n_,u0)) for k in range(n_+1)]+[X(at(t0+(t1-t0)*k/n_,u1)) for k in range(n_,-1,-1)]
            g.polygon(poly(-.1,1.03,-1,1),fill=SK,outline=OUT,width=max(1,int(2*ol)))
            for m,t0,t1,u0,u1 in [('CHEST',.62,.92,-.9,-.08),('CHEST',.62,.92,.08,.9),('ABS',.12,.58,-.35,.35),('OBLIQUES',.1,.5,-.95,-.5),('OBLIQUES',.1,.5,.5,.95),('LATS',.45,.8,-1,-.75),('LATS',.45,.8,.75,1),('TRAPS',.9,1.03,-.55,.55)]:
                c=hl(m)
                if c:g.polygon(poly(t0,t1,u0,u1),fill=c)
            c=hl('GLUTES')
            if c:circ(J['hR'],3.8*s,c);circ(J['hL'],3.8*s,c)
            head()
            for pr in ill['props']:prop(pr,'M')
            for sh,el,wr in (('sR','elbow','wrist'),('sL','elbow2','wrist2')):
                limb(J[sh],J[el],5.6,SK,hl('TRICEPS','BICEPS'))
                c=hl('FRONT_DELTS','REAR_DELTS')
                if c:circ(J[sh],3.8*s,c)
                limb(J[el],J[wr],4.6,SK,hl('FOREARMS'));hand(J[wr])
        for pr in ill['props']:prop(pr,'F')
    if title:
        g.text((8,4),title,fill='#BC6C25')
    if fn: img.save(fn)
    return img
