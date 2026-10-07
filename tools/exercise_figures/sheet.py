import sys
from figs import *
from PIL import Image
ids=[i for i in E if not sys.argv[2:] or i in sys.argv[2:]]
ims=[render(E[i],*MUS[i],W=600,Hh=280,title=i) for i in ids]
cols=3;rows=(len(ims)+cols-1)//cols
o=Image.new('RGB',(600*cols,285*rows),'white')
for k,im in enumerate(ims):o.paste(im,((k%cols)*600,(k//cols)*285))
o.save(sys.argv[1]);print(len(ims),len(MUS))
