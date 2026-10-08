# Náhled úvodu – věrný přepis IntroFlyoverView.kt (časování, kamera, scény)
import math, os, sys
from PIL import Image, ImageDraw, ImageFont
R='/home/claude/Makroflow/app/src/main/'
OUT=sys.argv[1]; FPS=int(sys.argv[2]) if len(sys.argv)>2 else 12
W,H=360,780
def L(p,mode='RGBA'): return Image.open(R+p).convert(mode)
maps=[L('res/drawable-nodpi/sky_pass.png','RGB'),L('res/drawable/mountains.png','RGB'),L('res/drawable/meadow.png','RGB'),L('res/drawable/poketown.webp','RGB')]
SKY,MOUNT,MEAD,TOWN=0,1,2,3
mines=L('res/drawable-nodpi/mines.png','RGB')
gud=L('res/drawable/gudwin_oliver.png'); bush=L('res/drawable-nodpi/portal_bush_a.png')
run=L('res/drawable/makromon_12_spirra.png').resize((320,320),Image.LANCZOS)
axe=L('assets/hero/axe_w.png'); mining=L('assets/hero/mining_e.png')
kingi=Image.open(R+'res/drawable/kral_mlsak.gif'); kingf=[]
try:
    for i in range(kingi.n_frames): kingi.seek(i); kingf.append(kingi.convert('RGBA'))
except EOFError: pass
font=lambda s: ImageFont.truetype(R+'res/font/jersey_15.ttf', int(s))
tops=[];y=0
for m in maps: tops.append(y); y+=m.height*W/m.width
worldH=y; minesH=mines.height*W/mines.width
def mapH(i): return maps[i].height*W/maps[i].width
def at(i,fx,fy): return (fx*W, tops[i]+fy*mapH(i))
TREE=(255/688,668/1536); BUSHP=(0.62,0.47); SILV=(100/150,338/540)
def k(t,p,z,dx=0,dy=0): return (t,False,p[0]+dx*W,p[1]+dy*W,z)
gudp=at(TOWN,.12,.52); tree=at(MEAD,*TREE); bp=at(MEAD,*BUSHP); mine=at(MOUNT,.13,.47); stat=at(MOUNT,.5,.6); silver=(SILV[0]*W,SILV[1]*minesH)
END=27600; DIS=800; TOTAL=END+DIS+200
KEYS=[k(0,at(TOWN,.5,.8),1.15),k(1400,at(TOWN,.45,.62),1.3),k(2700,gudp,2.4,.14,-.05),k(4300,gudp,2.5,.14,-.05),
 k(5500,(.5*W,tops[TOWN]),1.1),k(6700,tree,2.6,.09,-.03),k(8600,tree,2.7,.09,-.03),k(9300,bp,2.4,.05,-.04),k(10900,bp,2.2,.22,-.04),
 k(11900,at(MOUNT,.3,.75),1.4),k(12500,mine,2.4),k(12900,mine,7),(12900,True,silver[0],silver[1],3.2),(15800,True,silver[0],silver[1],2.6),
 k(16000,mine,7),k(16900,mine,2.2,.05),k(17900,stat,2.5,0,-.02),k(21800,stat,2.6,0,-.02),k(23300,at(MOUNT,.5,.08),1.4),
 k(24800,(.5*W,0),1),k(TOTAL,(.5*W,0),1)]
sm=lambda x:x*x*(3-2*x)
def cam(t):
    i=max([j for j,kk in enumerate(KEYS) if kk[0]<=t] or [0]); a=KEYS[i]; b=KEYS[i+1] if i+1<len(KEYS) else a
    x,y,z=a[2],a[3],a[4]
    if b is not a and b[1]==a[1] and b[0]>a[0]:
        q=sm(min(1,max(0,(t-a[0])/(b[0]-a[0])))); x=a[2]+(b[2]-a[2])*q; y=a[3]+(b[3]-a[3])*q; z=math.exp(math.log(a[4])+(math.log(b[4])-math.log(a[4]))*q)
    bot=minesH if a[1] else worldH; hw=W/(2*z); hh=H/(2*z)
    return a[1], min(max(x,hw),W-hw), min(max(y,hh),max(hh,bot-hh)), z
class Cam:
    def __init__(s,img,cx,cy,z): s.img,s.cx,s.cy,s.z=img,cx,cy,z
    def sx(s,x): return (x-s.cx)*s.z+W/2
    def sy(s,y): return (y-s.cy)*s.z+H/2
    def blit(s,src,x0,y0,w,h,smooth=False,rot=0,flip=False):
        X0,Y0=s.sx(x0),s.sy(y0); tw,th=w*s.z,h*s.z
        if X0>W or Y0>H or X0+tw<0 or Y0+th<0 or tw<1 or th<1: return
        # ořez na obrazovku
        cl=max(0,-X0); ct=max(0,-Y0); cr=max(0,X0+tw-W); cb=max(0,Y0+th-H)
        fx=src.width/tw; fy=src.height/th
        box=(int(cl*fx),int(ct*fy),int(math.ceil(src.width-cr*fx)),int(math.ceil(src.height-cb*fy)))
        if box[2]<=box[0] or box[3]<=box[1]: return
        part=src.crop(box); pw=int(round((box[2]-box[0])/fx)); ph=int(round((box[3]-box[1])/fy))
        if pw<1 or ph<1: return
        part=part.resize((pw,ph),Image.LANCZOS if smooth else Image.NEAREST)
        if flip: part=part.transpose(Image.FLIP_LEFT_RIGHT)
        if rot: part=part.rotate(rot,resample=Image.BICUBIC,expand=False)
        if part.mode!='RGBA': part=part.convert('RGBA')
        s.img.alpha_composite(part,(int(X0+cl),int(Y0+ct)))
    def rect(s,x0,y0,x1,y1,col):
        ImageDraw.Draw(s.img).rectangle((s.sx(x0),s.sy(y0),s.sx(x1),s.sy(y1)),fill=col)
    def oval(s,x0,y0,x1,y1,col):
        ov=Image.new('RGBA',(W,H),(0,0,0,0)); ImageDraw.Draw(ov).ellipse((s.sx(x0),s.sy(y0),s.sx(x1),s.sy(y1)),fill=col); s.img.alpha_composite(ov)
def cloudbmp():
    b=Image.new('RGBA',(32,14),(0,0,0,0)); d=ImageDraw.Draw(b)
    bl=[(8,9,5),(15,6,6),(23,8,5),(28,10,3.5),(4,11,3)]
    for x,y,r in bl: d.ellipse((x-r,y+1.5-r,x+r,y+1.5+r),fill=(215,207,168,255))
    for x,y,r in bl: d.ellipse((x-r,y-r,x+r,y+r),fill=(254,250,224,255))
    return b
CLOUD=cloudbmp()
def hero(c,strip,f,fx,fy,u): c.blit(strip.crop((f*64,0,f*64+64,40)),fx-32*u,fy-38*u,64*u,40*u)
def frameAt(t): return int(t/75)%10
def bits(c,pts,col,u,a):
    for px,py in pts: c.rect(px-u,py-u,px+u,py+u,col[:3]+(int(255*a),))
def world(c,t):
    for i,m in enumerate(maps): c.blit(m,0,tops[i],W,mapH(i))
    # Gudwin
    x,yy=gudp; s=W*.11; hop=math.sin((t-3000)/500*math.pi)*s*.35 if 3000<=t<=3500 else 0
    c.oval(x-s*.32,yy-s*.04,x+s*.32,yy+s*.04,(0,0,0,85)); c.blit(gud,x-s/2,yy-s-hop,s,s)
    if 3150<=t<=4500:
        f=font(W*.035*c.z); txt='Vítej, poutníku!'; d=ImageDraw.Draw(c.img); bx,by=c.sx(x+s*.2),c.sy(yy-s*1.15)
        tw=d.textlength(txt,font=f); pad=W*.012*c.z
        d.rounded_rectangle((bx-tw/2-pad-2,by-W*.035*c.z-pad-2,bx+tw/2+pad+2,by+pad*.6+2),radius=6,fill=(46,27,14))
        d.rounded_rectangle((bx-tw/2-pad,by-W*.035*c.z-pad,bx+tw/2+pad,by+pad*.6),radius=5,fill=(254,250,224))
        d.text((bx-tw/2,by-W*.035*c.z*0.95),txt,font=f,fill=(46,27,14))
    # dřevorubec
    u=W/205; tx,ty=tree; fx,fy=tx+.1*W,ty+.012*W; hero(c,axe,frameAt(t),fx,fy,u)
    lp=t%750
    if 420<=lp<=750:
        q=(lp-420)/330; hit=int(t/750); pts=[]
        for i in range(5):
            a=((hit*37+i*71)%100)/100; vx=(.3+a)*30*u*(1 if i%2==0 else .6); vy=-(20+25*((i*53+hit*11)%100)/100)*u
            pts.append((fx-14*u+vx*q, fy-10*u+vy*q+40*u*q*q))
        bits(c,pts,(196,138,74),u,1-q)
    # keř + makromon
    bx,by=bp; bw=W*.16; bh=bw*44/64; rt=t-9800; s=W*.13
    def drawrun():
        if rt<0 or rt>1600: return
        if rt<320:
            q=rt/320; x=bx+q*W*.08; y=by+s*.3*(1-q)-math.sin(q*math.pi)*s*.6; rot=8*(1-q)
        else:
            q=(rt-320)/1280; x=bx+W*.08+q*q*W*.9; g=((rt-320)%240)/240; y=by-abs(math.sin(g*math.pi))*s*.15; rot=-5*math.sin(g*2*math.pi)
            c.oval(x-s*.28,by-s*.035,x+s*.28,by+s*.035,(0,0,0,85))
        c.blit(run,x-s/2,y-s*.94,s,s,smooth=True,rot=-rot)
    if 0<=rt<=110: drawrun()
    rus=(3.5 if 9000<=t<=9800 else 0)*math.sin(t/35)
    c.blit(bush,bx-bw/2,by-bh,bw,bh,rot=-rus)
    if rt>110: drawrun()
    if 0<=rt<=700:
        q=rt/700; pts=[]
        for i in range(8):
            vx=((i*37)%100/100-.5)*70*u; vy=-(25+(i*53)%100/100*30)*u
            pts.append((bx+vx*q, by-bh*.6+vy*q+60*u*q*q))
        bits(c,pts,(104,160,60),u,1-q)
    # vykřičník u krále
    if 17600<=t<=22000:
        x,yy=stat; yy-=W*.24; bob=math.sin(t/180)*W*.006; f=font(W*.07*c.z); d=ImageDraw.Draw(c.img)
        d.text((c.sx(x)+2,c.sy(yy+bob)-W*.07*c.z+2),'!',font=f,fill=(10,15,6)); d.text((c.sx(x),c.sy(yy+bob)-W*.07*c.z),'!',font=f,fill=(255,226,122))
    for i in range(1,4):
        yy=tops[i]; cw=W*.42; ch=cw*14/32
        for row in (0,1):
            for n in range(-1,4):
                dr=((t/(60+i*7)+n*cw*.9+row*cw*.45+i*40)%(W+cw))-cw*.5
                y2=yy-ch*.8+row*ch*.55+math.sin(n+i)*ch*.15
                c.blit(CLOUD,dr-cw/2,y2,cw,ch)
def minesScene(c,t):
    c.blit(mines,0,0,W,minesH); u=W/150; fx=SILV[0]*W-13*u; fy=SILV[1]*minesH+12*u
    hero(c,mining,frameAt(t),fx,fy,u)
    lp=t%750
    if 450<=lp<=750:
        q=(lp-450)/300; hit=int(t/750); pts=[]
        for i in range(6):
            ang=(((hit*29+i*61)%100)/100)*math.pi+math.pi; sp=(12+i*3)*u
            pts.append((fx+11*u+math.cos(ang)*sp*q, fy-16*u+math.sin(ang)*sp*q+14*u*q*q))
        bits(c,pts,(255,226,122),u*.6,1-q)
    # lucerna
    cx,cy=c.sx(fx),c.sy(fy-10*u); r=70*u*c.z
    m=Image.new('L',(W,H),0); px=m.load()
    import numpy as np
    yy,xx=np.mgrid[0:H,0:W]; d=np.sqrt((xx-cx)**2+(yy-cy)**2)/r
    a=np.clip((d-.45)/(.55),0,1)*204
    sh=Image.fromarray(np.dstack([np.zeros((H,W)),np.zeros((H,W)),np.zeros((H,W)),a]).astype('uint8'),'RGBA')
    c.img.alpha_composite(sh)
def black(t):
    if t<700: return 1-t/700
    if 12400<=t<=12900: return (t-12400)/500
    if 12900<=t<=13300: return 1-(t-12900)/400
    if 15600<=t<=16000: return (t-15600)/400
    if 16000<=t<=16400: return 1-(t-16000)/400
    return 0
LINES=[(500,4300,'Daleko za tvým telefonem leží Makrosvět.'),(5600,8800,'Na louce se poctivě pracuje…'),(9000,11200,'…a v každém keři se může něco skrývat.'),
 (13300,15600,'Hluboko v dolech se kope vzácná ruda.'),(16500,17900,'Na horách vládne Král Mlsák.'),(23000,25200,'A za průsmykem čeká nový kraj…')]
def wrap(d,txt,f,width):
    out=[];cur=''
    for w_ in txt.split(' '):
        tst=(cur+' '+w_).strip()
        if d.textlength(tst,font=f)<=width: cur=tst
        else: out.append(cur); cur=w_
    if cur: out.append(cur)
    return out
def overlays(img,t):
    d=ImageDraw.Draw(img,'RGBA')
    for a_,b_,txt in LINES:
        if a_<=t<=b_:
            a=max(0,min(1,(t-a_)/300,(b_-t)/300)); shown=txt[:int(max(0,min(len(txt),(t-a_)/32)))]
            f=font(W*.058); pad=W*.05; top=H-W*.36
            d.rounded_rectangle((pad*.6,top,W-pad*.6,top+W*.22),radius=W*.03,fill=(10,15,6,int(a*190)))
            ls=wrap(d,shown,f,W-pad*2); lh=W*.058*.95; y0=top+(W*.22-lh*len(ls))/2
            for i,l in enumerate(ls): d.text((pad,y0+i*lh),l,font=f,fill=(254,250,224,int(a*255)))
    if 18000<=t<=21900:
        a=max(0,min(1,(t-18000)/250,(21900-t)/250)); msg='Kdo se opovažuje vstoupit do mého pohoří? … Á, nový trenér! Ukaž mi, co v tobě je.'
        shown=msg[:int(max(0,min(len(msg),(t-18300)/32)))]
        m=W*.04; bh=W*.42; top=H-bh-W*.12+(1-a)*W*.2
        d.rectangle((m,top,W-m,top+bh),fill=(46,27,14)); d.rectangle((m+3,top+3,W-m-3,top+bh-3),fill=(147,96,44)); d.rectangle((m+8,top+8,W-m-8,top+bh-8),fill=(240,226,190))
        ph=bh*1.25; pw=ph/2
        if kingf:
            fr=kingf[int(t/100)%len(kingf)].resize((int(pw),int(ph)),Image.NEAREST)
            img.alpha_composite(fr,(int(m+W*.02),int(top+bh-ph-W*.02)))
        tx=m+W*.04+pw; f=font(W*.045)
        d.text((tx,top+W*.08-W*.045),'KRÁL MLSÁK',font=f,fill=(188,108,37))
        for i,l in enumerate(wrap(d,shown,f,W-m-W*.05-tx)): d.text((tx,top+W*.1+i*W*.045*.95),l,font=f,fill=(46,27,14))
    if t>=25200:
        tt=t-25200; drop=sm(min(1,tt/650)); bounce=math.sin((tt-650)/350*math.pi)*W*.02 if 650<=tt<=1000 else 0
        size=W/4.6; y=-size+(H*.42+size)*drop-bounce; f=font(size); sh=max(3,size/12)
        tw=d.textlength('MAKROMON',font=f)
        d.text((W/2-tw/2+sh,y+sh-size*.8),'MAKROMON',font=f,fill=(10,15,6)); d.text((W/2-tw/2,y-size*.8),'MAKROMON',font=f,fill=(254,250,224))
        la=min(1,max(0,(tt-650)/400)); lw=tw*.6*la; d.rectangle((W/2-lw/2,y+sh*3,W/2+lw/2,y+sh*5),fill=(233,176,114))
        sa=min(1,max(0,(tt-1000)/500))
        if sa>0:
            f2=font(W*.06); s2='Tvoje dobrodružství začíná'; d.text((W/2-d.textlength(s2,font=f2)/2,y+size*.55-W*.06*.8),s2,font=f2,fill=(254,250,224,int(sa*255)))
        for i in range(10):
            q=((tt/900+i*.13)%1); sx=W*(.1+.8*((i*37)%100)/100); sy=y-size*.9+size*1.3*((i*53)%100)/100; r=W*.008*math.sin(q*math.pi)
            col=(255,226,122,int(math.sin(q*math.pi)*255)); d.rectangle((sx-r,sy-r*.3,sx+r,sy+r*.3),fill=col); d.rectangle((sx-r*.3,sy-r,sx+r*.3,sy+r),fill=col)
    if t<=END:
        f=font(W*.045); lab='Přeskočit >'; tw=d.textlength(lab,font=f); pad=W*.03; top=W*.12
        d.rounded_rectangle((W-tw-pad*3,top,W-pad,top+W*.045+pad*1.4),radius=20,fill=(10,15,6,150)); d.text((W-tw-pad*2,top+pad*.5),lab,font=f,fill=(254,250,224,230))
FLY=[(4600,6200),(11000,12300),(22200,24200)]
def flyclouds(img,t):
    for s_,(a,b) in enumerate(FLY):
        if not a<=t<=b: continue
        q=(t-a)/(b-a)
        for n in range(5):
            cw=W*(.5+.12*((n+s_)%3)); ch=cw*14/32; x=W*(.1+.2*n)+math.sin((n+s_)*1.7)*W*.12
            y=-ch+(H+ch*2)*min(1,max(0,q*1.6-n*.15))
            c=Cam(img,W/2,H/2,1); cl=CLOUD.copy(); cl.putalpha(cl.getchannel('A').point(lambda v:int(v*230/255))); c.blit(cl,x-cw/2,y,cw,ch)
MAP_UNDER=L('res/drawable-nodpi/mines.png','RGB')  # jen pro rozpad: pod úvodem je mapa
def render(t):
    mn,cx,cy,z=cam(t); img=Image.new('RGBA',(W,H),(0,0,0,255)); c=Cam(img,cx,cy,z)
    if mn: minesScene(c,t)
    else: world(c,t); flyclouds(img,t)
    b=black(t)
    if b>0: img.alpha_composite(Image.new('RGBA',(W,H),(0,0,0,int(b*255))))
    overlays(img,t)
    dis=min(1,max(0,(t-END)/DIS))
    if dis>0:
        under=Image.new('RGBA',(W,H),(60,80,40,255)); mask=Image.new('L',(W,H),0); md=ImageDraw.Draw(mask)
        cell=W/9; rows=math.ceil(H/cell); cxx=4; cyy=(rows-1)/2; mx=cxx+cyy
        for r_ in range(rows):
            for col in range(9):
                dd=(abs(col-cxx)+abs(r_-cyy))/mx; q=min(1,max(0,(dis-dd*.55)/.45)); size=(1-q)**2
                if size<=0: continue
                hf=cell*size/2; X=col*cell+cell/2; Y=r_*cell+cell/2; md.rectangle((X-hf,Y-hf,X+hf,Y+hf),fill=255)
        under.paste(img,(0,0),mask); img=under
    return img.convert('RGB')
os.makedirs(OUT,exist_ok=True)
n=int(TOTAL/1000*FPS)
for i in range(n):
    render(i*1000/FPS).save(f'{OUT}/f{i:04d}.png')
print('frames',n)
