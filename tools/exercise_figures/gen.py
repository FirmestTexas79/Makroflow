from figs import *
def f(x):
    x=round(float(x),2);s=('%g'%x)
    return s+'f'
def pt(p):return f'p({f(p[0])}, {f(p[1])})'
def pose_kt(p):
    if p is None:return 'null'
    a=[f'pelvis = {pt(p["pelvis"])}',f'torso = {f(p["torso"])}',f'wrist = {pt(p["wrist"])}',f'ankle = {pt(p["ankle"])}']
    if p.get('eb',-1)!=-1:a.append(f'elbowBend = {p["eb"]}')
    if p.get('kb',1)!=1:a.append(f'kneeBend = {p["kb"]}')
    if p.get('fa',0):a.append(f'footAngle = {f(p["fa"])}')
    if p.get('v','S')=='F':a.append('view = View.FRONT')
    for k,n in (('wrist2','wrist2'),('ankle2','ankle2')):
        if p.get(k) is not None:a.append(f'{n} = {pt(p[k])}')
    for k,n in (('eb2','elbowBend2'),('kb2','kneeBend2')):
        if k in p:a.append(f'{n} = {p[k]}')
    for k,n in (('fa2','footAngle2'),('as','armScale'),('as2','armScale2'),('ts','thighScale'),('sh','shrug')):
        if k in p:a.append(f'{n} = {f(p[k])}')
    return 'Pose('+', '.join(a)+')'
INKK={'F':'Ink.FRAME','S':'Ink.STEEL','C':'Ink.CUSHION','P':'Ink.PLATE','K':'Ink.SKIN'}
LAY={'B':'Layer.BACK','M':'Layer.MID','F':'Layer.FRONT'}
JJ={'W':'J.WRIST','W2':'J.WRIST2','E':'J.ELBOW','S':'J.SHOULDER','P':'J.PELVIS','K':'J.KNEE','K2':'J.KNEE2','A':'J.ANKLE','A2':'J.ANKLE2','H':'J.HEAD'}
def prop_kt(pr):
    k=pr[0]
    if k=='only':return f'Prop.Only({pr[1]}, {prop_kt(pr[2])})'
    if k=='bar':return f'Prop.Bar({pt(pr[1])}, {pt(pr[2])}, {f(pr[3])}, {INKK[pr[4]]}, {LAY[pr[5]]})'
    if k=='disc':return f'Prop.Disc({pt(pr[1])}, {f(pr[2])}, {INKK[pr[3]]}, {LAY[pr[4]]})'
    if k=='at':return f'Prop.At({JJ[pr[1]]}, {f(pr[2])}, {INKK[pr[3]]}, {LAY[pr[4]]})'
    if k=='to':return f'Prop.ToJoint({pt(pr[1])}, {JJ[pr[2]]}, {f(pr[3])}, {INKK[pr[4]]}, {LAY[pr[5]]})'
    if k=='pad':return f'Prop.Pad({JJ[pr[1]]}, {pt(pr[2])}, {pt(pr[3])}, {f(pr[4])}, {INKK[pr[5]]}, {LAY[pr[6]]})'
    raise Exception(k)
out=['''package cz.uhk.macroflow.training.figure

import cz.uhk.macroflow.training.figure.ExerciseFigures.Illustration
import cz.uhk.macroflow.training.figure.ExerciseFigures.Ink
import cz.uhk.macroflow.training.figure.ExerciseFigures.J
import cz.uhk.macroflow.training.figure.ExerciseFigures.Layer
import cz.uhk.macroflow.training.figure.ExerciseFigures.P
import cz.uhk.macroflow.training.figure.ExerciseFigures.Pose
import cz.uhk.macroflow.training.figure.ExerciseFigures.Prop
import cz.uhk.macroflow.training.figure.ExerciseFigures.View

/**
 * Pózy a náčiní všech cviků (docs/adr/0066). Vygenerováno skriptem tools/exercise_figures/gen.py z figs.py,
 * kde se pózy ladí okem na kontaktních arších (sheet.py). Neupravovat ručně – změnit figs.py a vygenerovat znovu.
 */
internal object ExerciseFigureData {
    private fun p(x: Float, y: Float) = P(x, y)

    val BY_ID: Map<String, Illustration> by lazy {
        mapOf(''']
items=[]
for i,e in E.items():
    props=',\n                '.join(prop_kt(pr) for pr in e['props'])
    items.append(f'''            "{i}" to Illustration(
                {pose_kt(e['start'])},
                {pose_kt(e['end'])},
                listOf(
                {props}
                )
            )''')
out.append(',\n'.join(items))
out.append('''        )
    }
}
''')
open(os.path.join(ROOT,'app/src/main/java/cz/uhk/macroflow/training/figure/ExerciseFigureData.kt'),'w',encoding='utf-8').write('\n'.join(out))
print(len(items))
