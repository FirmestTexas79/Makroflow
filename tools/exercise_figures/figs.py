import re,math
from engine import *
import os
ROOT=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..')
lib=open(os.path.join(ROOT,'app/src/main/java/cz/uhk/macroflow/training/exercises/ExerciseLibrary.kt'),encoding='utf-8').read()
MUS={}
for m in re.finditer(r'ex\("([a-z0-9_]+)",[^\n]*\n?\s*(?:primary = )?setOf\(([^)]*)\),\s*(?:secondary = )?(setOf\(([^)]*)\)|emptySet\(\))',lib):
    MUS[m.group(1)]=(set(x.strip() for x in m.group(2).split(',') if x.strip()),set(x.strip() for x in (m.group(4) or '').split(',') if x.strip()))
P=lambda x,y:(round(x,1),round(y,1))
def fk(o,ang,l):return P(o[0]+math.cos(math.radians(ang))*l,o[1]+math.sin(math.radians(ang))*l)
def sh(pel,torso,extra=0):return fk(pel,torso,30+extra)
def kneel(px,py):
    th=math.degrees(math.asin(max(-1,min(1,(3-py)/24))));th=-180-th  # koleno vzadu
    k=fk((px,py),th,24);return P(k[0]-23,3)
# props
B,M,F='B','M','F'
def floor(x0,x1):return [('bar',P(x0,0),P(x1,0),2,'S',B)]
def bench(x0,x1,top=27):return [('bar',P(x0,top),P(x1,top),5,'C',B),('bar',P(x0+8,top-2),P(x0+8,3),3,'F',B),('bar',P(x1-8,top-2),P(x1-8,3),3,'F',B),('bar',P(x0+2,2),P(x0+14,2),3,'F',B),('bar',P(x1-14,2),P(x1-2,2),3,'F',B)]
def seat(x0=28,x1=52,top=25,post=40):return [('bar',P(x0,top),P(x1,top),6,'C',B),('bar',P(post,3),P(post,top-1),4,'F',B),('bar',P(post-14,2),P(post+14,2),4,'F',B)]
def backrest(a,b):return [('bar',a,b,6,'C',B)]
def barbell(j='W',r=8.5):return [('at',j,r,'P',M),('at',j,2,'S',F)]
def dumbbell(j='W'):return [('at',j,3.6,'P',F)]
def handle(j='W'):return [('at',j,2.2,'F',F)]
def cable(frm,j='W',layer=B):return [('disc',frm,3,'S',B),('to',frm,j,1.2,'S',layer)]
def column(x,top,beam_to=None):
    r=[('bar',P(x,0),P(x,top),5,'F',B)]
    if beam_to is not None:r.append(('bar',P(x,top),P(beam_to,top),4,'F',B))
    return r
def pose(**k):
    k.setdefault('v','S');return k
STAND=dict(pelvis=P(40,49),torso=90,ankle=P(41,2),kb=1)
E={}
def ex(i,start,end,props):E[i]=dict(start=start,end=end,props=props)

# ── PUSH ───────────────────────────────────────────────
BENCH_START=pose(pelvis=P(60,36),torso=180,wrist=P(32,47),ankle=P(78,2),eb=-1,kb=1)
BENCH_END=pose(pelvis=P(60,36),torso=180,wrist=P(31,65),ankle=P(78,2),eb=-1,kb=1)
ex('bench_press',BENCH_START,BENCH_END,bench(8,90)+barbell()+floor(0,96))
ex('close_grip_bench',BENCH_START,BENCH_END,bench(8,90)+barbell()+floor(0,96))
ex('skull_crusher',pose(pelvis=P(60,36),torso=180,wrist=P(14,48),ankle=P(82,34),eb=-1,kb=1),pose(pelvis=P(60,36),torso=180,wrist=P(29,65),ankle=P(82,34),eb=-1,kb=1),bench(8,90)+barbell()+floor(0,96))
s=sh((40,30),120)
ex('incline_db_press',pose(pelvis=P(40,30),torso=120,wrist=P(s[0]+8,s[1]+3),ankle=P(62,3),eb=-1),pose(pelvis=P(40,30),torso=120,wrist=fk(s,30,27),ankle=P(62,3),eb=-1),
   seat()+backrest(P(36,28),P(20,56))+dumbbell()+floor(0,80))
ex('chest_dip',pose(pelvis=P(40,50),torso=75,wrist=P(49,50),ankle=P(23,38),eb=-1,kb=1,fa=200),pose(pelvis=P(40,36),torso=65,wrist=P(49,50),ankle=P(23,24),eb=-1,kb=1,fa=200),
   [('bar',P(36,0),P(36,48),3,'F',B),('bar',P(64,0),P(64,48),3,'F',B),('bar',P(32,48),P(68,48),3,'F',M)]+floor(20,80))
ex('cable_fly',pose(v='F',pelvis=P(40,49),torso=90,wrist=P(74,82),ankle=P(46,2),eb=-1),pose(v='F',pelvis=P(40,49),torso=90,wrist=P(44,66),ankle=P(46,2),eb=-1),
   column(84,104)+column(-4,104)+cable(P(82,100),'W')+cable(P(-2,100),'W2')+handle('W')+handle('W2')+floor(-8,88))
ex('push_up',pose(pelvis=P(32,24),torso=15,wrist=P(61,2),ankle=P(-13,7),fa=-75,eb=-1),pose(pelvis=P(30,10),torso=12,wrist=P(61,2),ankle=P(-14,5),fa=-75,eb=-1),floor(-24,72))
ex('overhead_press',pose(**STAND,wrist=P(46,80),eb=-1),pose(**STAND,wrist=P(41,108),eb=-1),barbell()+floor(20,62))
s=sh((40,30),90)
ex('db_shoulder_press',pose(pelvis=P(40,30),torso=90,wrist=P(45,64),ankle=P(60,3),eb=-1),pose(pelvis=P(40,30),torso=90,wrist=P(41,89),ankle=P(60,3),eb=-1),
   seat()+backrest(P(34,27),P(34,64))+dumbbell()+floor(10,72))
ex('machine_shoulder_press',pose(pelvis=P(40,30),torso=90,wrist=P(45,64),ankle=P(60,3),eb=-1),pose(pelvis=P(40,30),torso=90,wrist=P(41,89),ankle=P(60,3),eb=-1),
   seat()+backrest(P(34,27),P(34,64))+[('bar',P(24,3),P(24,40),4,'F',B),('disc',P(24,40),2.5,'S',B),('to',P(24,40),'W',3,'S',B)]+handle()+floor(10,72))
ex('lateral_raise',pose(v='F',**{k:v for k,v in STAND.items()},wrist=P(52,52),ankle2=None) if False else pose(v='F',pelvis=P(40,49),torso=90,ankle=P(46,2),wrist=P(50,50),eb=1),
   pose(v='F',pelvis=P(40,49),torso=90,ankle=P(46,2),wrist=P(76,78),eb=1),dumbbell('W')+dumbbell('W2')+floor(10,70))
ex('cable_lateral_raise',pose(v='F',pelvis=P(40,49),torso=90,ankle=P(46,2),wrist=P(42,52),eb=1,wrist2=P(30,62),eb2=-1),
   pose(v='F',pelvis=P(40,49),torso=90,ankle=P(46,2),wrist=P(76,78),eb=1,wrist2=P(30,62),eb2=-1),column(4,40)+cable(P(6,8),'W')+handle()+floor(0,80))
ex('front_raise',pose(**STAND,wrist=P(43,51),eb=-1),pose(**STAND,wrist=P(68,80),eb=-1),dumbbell()+floor(20,74))
ex('pec_deck',pose(v='F',pelvis=P(40,32),torso=90,ankle=P(48,3),ts=.3,wrist=P(70,92),eb=1),pose(v='F',pelvis=P(40,32),torso=90,ankle=P(48,3),ts=.3,wrist=P(45,90),eb=1),
   [('bar',P(40,30),P(40,84),16,'C',B),('bar',P(24,30),P(56,30),5,'C',B)]+column(4,108)+column(76,108)+[('at','W',3,'C',F),('at','W2',3,'C',F)]+floor(0,80))
ex('machine_chest_press',pose(pelvis=P(40,30),torso=92,wrist=P(47,60),ankle=P(60,3),eb=-1),pose(pelvis=P(40,30),torso=92,wrist=P(68,61),ankle=P(60,3),eb=-1),
   seat()+backrest(P(35,27),P(35,66))+[('bar',P(28,3),P(30,100),4,'F',B),('disc',P(30,98),2.5,'S',B),('to',P(30,98),'W',3,'S',M)]+handle()+floor(10,76))
s=sh((40,30),115)
ex('incline_machine_press',pose(pelvis=P(40,30),torso=115,wrist=P(38,57),ankle=P(62,7),eb=-1),pose(pelvis=P(40,30),torso=115,wrist=P(52,70),ankle=P(62,7),eb=-1),
   [('bar',P(6,3),P(72,3),4,'F',B),('bar',P(18,3),P(24,96),4,'F',B),('bar',P(38,3),P(38,24),4,'F',B),('bar',P(28,25),P(52,25),6,'C',B),('bar',P(35,28),P(21,60),6,'C',B),('disc',P(26,98),3,'S',B),('to',P(26,98),'W',3,'S',M)]+handle())
# triceps
ex('triceps_pushdown',pose(pelvis=P(40,49),torso=85,ankle=P(41,2),wrist=P(52,72),eb=-1),pose(pelvis=P(40,49),torso=85,ankle=P(41,2),wrist=P(44,49),eb=-1),
   column(68,108,60)+cable(P(62,105))+handle()+floor(20,74))
s=sh((40,47),75)
ex('overhead_extension',pose(pelvis=P(40,47),torso=75,ankle=P(50,2),ankle2=P(28,2),wrist=P(40,86),eb=-1),pose(pelvis=P(40,47),torso=75,ankle=P(50,2),ankle2=P(28,2),wrist=P(73,95),eb=-1),
   column(2,80)+cable(P(6,72))+handle()+floor(0,84))
s=sh((34,46),20)
e=fk(s,200,16)
ex('triceps_kickback',pose(pelvis=P(34,46),torso=20,ankle=P(40,2),ankle2=P(26,2),wrist=P(e[0],e[1]-14),eb=1,wrist2=P(74,29),eb2=-1),
   pose(pelvis=P(34,46),torso=20,ankle=P(40,2),ankle2=P(26,2),wrist=fk(s,198,29.5),eb=1,wrist2=P(74,29),eb2=-1),bench(52,96)+dumbbell()+floor(10,98))

# ── PULL ───────────────────────────────────────────────
ex('face_pull',pose(**STAND,wrist=P(69,82),eb=-1),pose(**STAND,wrist=P(44,86),eb=1),column(94,100)+cable(P(90,86))+handle()+floor(20,98))
s=sh((36,46),15)
ex('reverse_fly',pose(pelvis=P(36,46),torso=15,ankle=P(42,2),wrist=P(s[0]+1,s[1]-29),eb=-1),pose(pelvis=P(36,46),torso=15,ankle=P(42,2),wrist=P(s[0]-1,s[1]+9),eb=1,**{'as':.35}),dumbbell()+floor(20,80))
ex('barbell_curl',pose(**STAND,wrist=P(43,51),eb=-1),pose(**STAND,wrist=P(47,77),eb=-1),barbell()+floor(20,66))
ex('ez_bar_curl',pose(**STAND,wrist=P(43,51),eb=-1),pose(**STAND,wrist=P(47,77),eb=-1),barbell()+floor(20,66))
ex('hammer_curl',pose(**STAND,wrist=P(43,51),eb=-1),pose(**STAND,wrist=P(47,77),eb=-1),dumbbell()+floor(20,66))
s=sh((40,30),125)
ex('incline_db_curl',pose(pelvis=P(40,30),torso=125,ankle=P(62,3),wrist=P(s[0],s[1]-29),eb=-1),pose(pelvis=P(40,30),torso=125,ankle=P(62,3),wrist=P(s[0]+9,s[1]-5),eb=-1),
   seat()+backrest(P(36,28),P(16,57))+dumbbell()+floor(0,80))
s=sh((40,30),85); e=fk(s,-40,16)
ex('preacher_curl',pose(pelvis=P(40,30),torso=85,ankle=P(62,3),wrist=fk(e,-50,14),eb=1),pose(pelvis=P(40,30),torso=85,ankle=P(62,3),wrist=fk(e,110,14),eb=-1),
   seat()+[('bar',P(e[0]-10,e[1]+6),P(e[0]+1,e[1]-4),5,'C',B),('bar',P(e[0]+2,e[1]-6),P(e[0]+6,3),3,'F',B),('to',P(e[0],e[1]-1),'W',3,'S',M)]+handle()+floor(10,80))
ex('single_arm_supported_curl',pose(pelvis=P(40,30),torso=85,ankle=P(62,3),wrist=fk(e,-50,14),eb=1),pose(pelvis=P(40,30),torso=85,ankle=P(62,3),wrist=fk(e,110,14),eb=-1),
   seat()+[('bar',P(e[0]-10,e[1]+6),P(e[0]+1,e[1]-4),5,'C',B),('bar',P(e[0]+2,e[1]-6),P(e[0]+6,3),3,'F',B)]+dumbbell()+floor(10,80))
# zápěstí: sed, předloktí na stehnech, ruka s činkou nad kolenem
WC=[('only',0,('to',P(69,28),'W',3.6,'K',F)),('only',1,('to',P(68,39),'W',3.6,'K',F))]
ex('wrist_curl',pose(pelvis=P(40,30),torso=70,ankle=P(64,3),wrist=P(64,35),eb=-1),pose(pelvis=P(40,30),torso=70,ankle=P(64,3),wrist=P(64,35),eb=-1),
   bench(14,58,25)+WC+[('only',0,('disc',P(70,27),5,'P',F)),('only',1,('disc',P(69,40),5,'P',F))]+floor(0,80))
ex('reverse_wrist_curl',pose(pelvis=P(40,30),torso=70,ankle=P(64,3),wrist=P(64,35),eb=-1),pose(pelvis=P(40,30),torso=70,ankle=P(64,3),wrist=P(64,35),eb=-1),
   bench(14,58,25)+WC+[('only',0,('disc',P(70,27),3.6,'P',F)),('only',1,('disc',P(69,40),3.6,'P',F))]+floor(0,80))
ex('farmers_walk',pose(pelvis=P(40,49),torso=90,ankle=P(52,3),ankle2=P(28,3),wrist=P(41,50)),pose(pelvis=P(44,49),torso=90,ankle=P(32,3),ankle2=P(56,3),wrist=P(45,50)),
   dumbbell('W')+[('at','W',4.2,'P',F)]+floor(14,70))
ex('shrug',pose(**STAND,wrist=P(41,50)),pose(**STAND,wrist=P(41,53.5),sh=3.5),dumbbell()+floor(20,62))
# hrazda
PB=[('bar',P(41,118),P(80,118),3,'F',B),('bar',P(80,118),P(80,0),4,'F',B),('disc',P(41,118),2.2,'F',F)]+floor(20,90)
ex('pull_up',pose(pelvis=P(40,59),torso=90,wrist=P(41,117),ankle=P(36,14),kb=-1,eb=1),pose(pelvis=P(40,80),torso=90,wrist=P(41,117),ankle=P(36,35),kb=-1,eb=1,**{'as':.75}),PB)
ex('chin_up',pose(pelvis=P(40,59),torso=90,wrist=P(41,117),ankle=P(36,14),kb=-1,eb=1),pose(pelvis=P(40,80),torso=90,wrist=P(43,116),ankle=P(36,35),kb=-1,eb=-1,**{'as':.75}),PB)
LP=[('bar',P(28,3),P(86,3),4,'F',B),('bar',P(82,3),P(82,114),5,'F',B),('bar',P(82,114),P(38,114),4,'F',B),('disc',P(40,111),3.2,'S',B),('bar',P(40,3),P(40,24),4,'F',B),('bar',P(30,25),P(50,25),6,'C',B),('bar',P(70,38),P(82,38),3,'F',B),('bar',P(58,38),P(70,38),6,'C',B),('to',P(40,108),'W',1.2,'S',B)]+handle()
ex('lat_pulldown',pose(pelvis=P(40,30),torso=95,wrist=P(41,93),ankle=P(64,6),eb=1),pose(pelvis=P(40,30),torso=100,wrist=P(40,57),ankle=P(64,6),eb=-1),LP)
ex('close_grip_pulldown',pose(pelvis=P(40,30),torso=95,wrist=P(42,92),ankle=P(64,6),eb=1),pose(pelvis=P(40,30),torso=102,wrist=P(42,58),ankle=P(64,6),eb=-1),LP)
s=sh((34,46),25)
ex('barbell_row',pose(pelvis=P(34,46),torso=25,ankle=P(40,2),wrist=P(s[0]+1,s[1]-29),eb=-1),pose(pelvis=P(34,46),torso=25,ankle=P(40,2),wrist=P(52,44),eb=-1),barbell()+floor(10,80))
s=sh((30,46),15)
ex('db_row',pose(pelvis=P(30,46),torso=15,ankle=P(36,2),ankle2=P(22,2),wrist=P(s[0]+1,s[1]-28),wrist2=P(72,29),eb=-1),pose(pelvis=P(30,46),torso=15,ankle=P(36,2),ankle2=P(22,2),wrist=P(48,46),wrist2=P(72,29),eb=-1),
   bench(50,94)+dumbbell()+floor(10,98))
ex('straight_arm_pulldown',pose(pelvis=P(40,47),torso=75,ankle=P(42,2),wrist=P(63,101),eb=-1),pose(pelvis=P(40,47),torso=75,ankle=P(42,2),wrist=P(50,47),eb=-1),
   column(92,112,84)+cable(P(86,109))+handle()+floor(20,96))
MR=seat()+[('bar',P(58,36),P(66,64),6,'C',B),('bar',P(66,64),P(84,94),3,'F',B),('bar',P(84,94),P(84,0),4,'F',B),('disc',P(84,94),2.5,'S',B),('to',P(84,94),'W',3,'S',B)]+handle()+floor(10,90)
ex('machine_row',pose(pelvis=P(40,30),torso=75,ankle=P(60,3),wrist=P(76,58),eb=-1),pose(pelvis=P(40,30),torso=75,ankle=P(60,3),wrist=P(52,55),eb=-1),MR)
CR=[('bar',P(6,8),P(80,8),5,'C',B),('bar',P(80,3),P(80,26),4,'F',B),('bar',P(78,4),P(78,24),3,'F',B),('disc',P(82,32),3,'S',B),('to',P(82,32),'W',1.2,'S',B)]+handle()+floor(0,90)
ex('seated_cable_row',pose(pelvis=P(30,14),torso=82,ankle=P(72,14),kb=1,wrist=P(60,40),eb=-1),pose(pelvis=P(30,14),torso=96,ankle=P(72,14),kb=1,wrist=P(38,32),eb=-1),CR)
ex('wide_cable_row',pose(pelvis=P(30,14),torso=82,ankle=P(72,14),kb=1,wrist=P(60,42),eb=-1),pose(pelvis=P(30,14),torso=96,ankle=P(72,14),kb=1,wrist=P(36,40),eb=-1),CR)
RPD=[('bar',P(40,30),P(40,84),16,'C',B),('bar',P(24,30),P(56,30),5,'C',B)]+column(4,108)+column(76,108)+handle('W')+handle('W2')+floor(0,80)
ex('reverse_pec_deck',pose(v='F',pelvis=P(40,32),torso=90,ankle=P(48,3),ts=.3,wrist=P(45,79),eb=1,**{'as':.35}),pose(v='F',pelvis=P(40,32),torso=90,ankle=P(48,3),ts=.3,wrist=P(76,82),eb=1),RPD)

# ── CORE ───────────────────────────────────────────────
s=(60,17.5);pl=fk(s,188,30)
ex('plank',pose(pelvis=pl,torso=8,wrist=P(74,3),ankle=P(-14,5),fa=-70,eb=-1),None,floor(-24,84))
kn=kneel(40,27)
CC=column(92,112,84)+cable(P(86,108))+handle()+floor(0,96)
ex('cable_crunch',pose(pelvis=P(40,27),torso=80,ankle=kn,fa=180,wrist=P(50,64),eb=-1),pose(pelvis=P(40,27),torso=25,ankle=kn,fa=180,wrist=P(72,44),eb=-1),CC)
ex('hanging_leg_raise',pose(pelvis=P(40,60),torso=90,wrist=P(41,119),ankle=P(41,14),eb=1),pose(pelvis=P(40,60),torso=90,wrist=P(41,119),ankle=P(86,60),eb=1,kb=1),
   [('bar',P(41,120),P(80,120),3,'F',B),('bar',P(80,120),P(80,0),4,'F',B),('disc',P(41,120),2.2,'F',F)]+floor(20,92))
ex('ab_wheel',pose(pelvis=P(32,25),torso=40,ankle=kneel(32,25),fa=180,wrist=P(57,10),eb=-1),pose(pelvis=P(44,14),torso=8,ankle=kneel(44,14),fa=180,wrist=P(100,8),eb=-1),
   [('at','W',4.5,'F',F),('at','W',1.6,'S',F)]+floor(-8,108))
ex('pallof_press',pose(pelvis=P(40,49),torso=90,ankle=P(48,2),ankle2=P(34,2),wrist=P(47,72),eb=-1),pose(pelvis=P(40,49),torso=90,ankle=P(48,2),ankle2=P(34,2),wrist=P(68,74),eb=-1),
   [('bar',P(20,0),P(20,80),5,'F',B),('disc',P(20,74),3,'S',B),('to',P(20,74),'W',1.2,'S',B)]+handle()+floor(10,80))
ex('side_plank',pose(v='F',pelvis=P(30,14),torso=15,wrist=P(63,2),wrist2=P(58,50),ankle=P(-14,4),ankle2=P(-14,9),eb=1,eb2=1,kb=1,kb2=1,**{'as':.45,'as2':1}),None,floor(-24,80))
ex('woodchop',pose(v='F',pelvis=P(40,49),torso=95,ankle=P(48,2),wrist=P(70,100),wrist2=P(70,98),eb=1,eb2=-1),pose(v='F',pelvis=P(40,49),torso=85,ankle=P(48,2),wrist=P(22,54),wrist2=P(20,56),eb=1,eb2=-1),
   column(90,112)+cable(P(88,106))+handle()+floor(0,96))
# ── ZÁDA / HÝŽDĚ ───────────────────────────────────────
ex('deadlift',pose(pelvis=P(30,27),torso=25,ankle=P(54,2),wrist=P(57,10),eb=-1),pose(pelvis=P(52,49),torso=90,ankle=P(54,2),wrist=P(53,50),eb=-1),barbell(r=10)+floor(30,80))
ex('rdl',pose(pelvis=P(40,49),torso=90,ankle=P(44,2),wrist=P(41,50),eb=-1),pose(pelvis=P(34,46),torso=20,ankle=P(44,2),wrist=P(61,28),eb=-1),barbell()+floor(20,80))
def chest(pel,t):  # ruce zkřížené na hrudi
    sv=sh(pel,t);return fk(fk(pel,t,23),t-90,8)
BE=[('bar',P(10,0),P(72,0),4,'F',B),('bar',P(20,14),P(52,44),4,'F',B),('bar',P(20,14),P(16,0),4,'F',B),('bar',P(56,40),P(60,0),4,'F',B),('bar',P(52,47),P(62,37),6,'C',B),('disc',P(27,21),3.2,'C',F)]
ex('back_extension',pose(pelvis=P(52,52),torso=-75,ankle=P(22,16),kb=-1,wrist=chest((52,52),-75),eb=-1,fa=-45),pose(pelvis=P(52,52),torso=50,ankle=P(22,16),kb=-1,wrist=chest((52,52),50),eb=-1,fa=-45),BE)
qk=kneel(30,27)
ex('bird_dog',pose(pelvis=P(30,27),torso=5,ankle=qk,fa=180,wrist=P(60,2),eb=-1),pose(pelvis=P(30,27),torso=5,ankle=qk,fa=180,ankle2=P(-16,29),kb2=1,fa2=180,wrist=P(89,33),wrist2=P(60,2),eb=-1),floor(-24,96))
HT=[('bar',P(-12,20),P(14,20),5,'C',B),('bar',P(-6,18),P(-6,3),3,'F',B),('bar',P(8,18),P(8,3),3,'F',B)]+barbell()+floor(-16,80)
p0=(40,12);p1=(42,23)
ex('hip_thrust',pose(pelvis=p0,torso=159,ankle=P(62,2),wrist=fk(p0,159-90,7.5),eb=1),pose(pelvis=p1,torso=180,ankle=P(62,2),wrist=fk(p1,90,7.5),eb=1),HT)
ex('bulgarian_split_squat',pose(pelvis=P(40,47),torso=85,ankle=P(58,2),ankle2=P(8,24),kb2=-1,fa2=180,wrist=P(43,48)),pose(pelvis=P(36,28),torso=82,ankle=P(58,2),ankle2=P(8,24),kb2=-1,fa2=180,wrist=P(40,29)),
   [('bar',P(-4,22),P(16,22),5,'C',B),('bar',P(6,20),P(6,3),3,'F',B)]+dumbbell()+floor(-8,72))
ex('hip_abduction',pose(v='F',pelvis=P(40,30),torso=90,ankle=P(45,3),ts=.3,wrist=P(54,32)),pose(v='F',pelvis=P(40,30),torso=90,ankle=P(60,3),ts=.3,wrist=P(54,32)),
   [('bar',P(40,30),P(40,82),16,'C',B),('bar',P(24,30),P(56,30),5,'C',B),('pad','K',P(4,5),P(4,-8),3,'C',F),('pad','K2',P(-4,5),P(-4,-8),3,'C',F)]+floor(10,74))
# ── NOHY ───────────────────────────────────────────────
s0=sh((40,49),90);s1=sh((30,26),55)
ex('back_squat',pose(pelvis=P(40,49),torso=90,ankle=P(44,2),wrist=P(s0[0]-4,s0[1]-1),eb=-1),pose(pelvis=P(30,26),torso=55,ankle=P(44,2),wrist=P(s1[0]-4,s1[1]-1),eb=-1),barbell()+floor(10,70))
LPR=[('bar',P(0,2),P(98,2),4,'F',B),('bar',P(56,26),P(96,66),3,'F',B),('bar',P(96,66),P(96,2),4,'F',B),('bar',P(42,15),P(16,41),6,'C',B),('bar',P(34,14),P(50,20),6,'C',B),('bar',P(42,14),P(42,2),4,'F',B),('pad','A',P(-3,9),P(9,-3),4,'F',M)]
ex('leg_press',pose(pelvis=P(40,20),torso=135,ankle=P(55,40),kb=1,fa=45,wrist=P(44,24),eb=1),pose(pelvis=P(40,20),torso=135,ankle=P(71,52),kb=1,fa=45,wrist=P(44,24),eb=1),LPR)
ex('single_leg_press',pose(pelvis=P(40,20),torso=135,ankle=P(55,40),ankle2=P(54,8),kb2=1,kb=1,fa=45,wrist=P(44,24),eb=1),pose(pelvis=P(40,20),torso=135,ankle=P(71,52),ankle2=P(54,8),kb2=1,kb=1,fa=45,wrist=P(44,24),eb=1),LPR)
LE=seat()+backrest(P(34,27),P(30,64))+[('bar',P(64,31),P(64,3),4,'F',B),('disc',P(64,31),2.5,'S',B),('to',P(64,31),'A',2.5,'S',B),('at','A',3.2,'C',F)]+floor(10,96)
ex('leg_extension',pose(pelvis=P(40,30),torso=100,ankle=P(66,8),kb=1,wrist=P(44,28),eb=1),pose(pelvis=P(40,30),torso=100,ankle=P(87,33),kb=1,wrist=P(44,28),eb=1),LE)
SLC=LE+[('bar',P(56,38),P(68,38),5,'C',F)]
ex('seated_leg_curl',pose(pelvis=P(40,30),torso=100,ankle=P(87,33),kb=1,wrist=P(44,28),eb=1),pose(pelvis=P(40,30),torso=100,ankle=P(58,10),kb=1,wrist=P(44,28),eb=1),SLC)
LLC=[('bar',P(8,24),P(84,24),5,'C',B),('bar',P(20,22),P(20,3),3,'F',B),('bar',P(72,22),P(72,3),3,'F',B),('bar',P(6,2),P(86,2),3,'F',B),('disc',P(14,26),2.5,'S',B),('to',P(14,26),'A',2.5,'S',B),('at','A',3.2,'C',F)]
ex('lying_leg_curl',pose(pelvis=P(40,30),torso=0,ankle=P(-6,29),kb=-1,wrist=P(80,22),eb=-1,fa=180),pose(pelvis=P(40,30),torso=0,ankle=P(28,52),kb=-1,wrist=P(80,22),eb=-1,fa=150),LLC)
p1=fk((30,3),40,24)
ex('nordic_curl',pose(pelvis=P(30,27),torso=90,ankle=P(7,3),fa=180,wrist=chest((30,27),90)),pose(pelvis=p1,torso=40,ankle=P(7,3),fa=180,wrist=P(80,8),eb=-1),
   [('bar',P(14,0),P(46,0),3,'F',B),('at','A',2.8,'C',F)]+floor(0,90))
ex('walking_lunge',pose(pelvis=P(40,49),torso=90,ankle=P(42,2),ankle2=P(39,2),wrist=P(41,50)),pose(pelvis=P(38,28),torso=88,ankle=P(60,2),kb=1,ankle2=P(14,4),kb2=1,fa2=-50,wrist=P(39,29)),dumbbell()+floor(0,74))
HS=[('bar',P(26,3),P(10,86),3,'F',B),('bar',P(40,3),P(62,8),3,'F',B),('pad','S',P(-6.9,-1.2),P(-1.7,-30.7),5,'C',B),('pad','S',P(-3,4),P(5,4),4,'C',F)]+floor(0,70)
ex('hack_squat',pose(pelvis=P(40,47),torso=100,ankle=P(48,6),fa=15,wrist=P(40,82),eb=-1),pose(pelvis=P(43.8,25.3),torso=100,ankle=P(48,6),fa=15,wrist=P(43,61),eb=-1),HS)
CALF=[('bar',P(40,2),P(60,2),4,'F',B),('pad','S',P(-4,4),P(6,4),4,'C',F)]+floor(20,64)
ex('standing_calf_raise',pose(pelvis=P(40,51),torso=90,ankle=P(41,4),fa=-10,wrist=P(44,84),eb=-1),pose(pelvis=P(40,56),torso=90,ankle=P(41,9),fa=-50,wrist=P(44,89),eb=-1),CALF)
ex('seated_calf_raise',pose(pelvis=P(40,30),torso=90,ankle=P(64,8),fa=12,wrist=P(62,38),eb=-1),pose(pelvis=P(40,33),torso=90,ankle=P(64,12),fa=-30,wrist=P(62,41),eb=-1),
   seat()+[('bar',P(64,4),P(76,4),4,'F',B),('pad','K',P(-4,5),P(6,5),4,'C',F)]+floor(10,82))
