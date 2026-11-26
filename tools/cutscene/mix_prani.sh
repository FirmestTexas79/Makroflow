#!/bin/sh
# Zvuk cutscény konce III (ElevenLabs: hudba, runy, výbuch světla, lucerna, Kesh, šepot) → build/ending_prani.m4a. Časy sedí s ending_wish.py.
A=${1:-audio/prani}
ffmpeg -y -loglevel error \
  -i $A/music.mp3 -i $A/runes.mp3 -i $A/burst.mp3 -i $A/lantern.mp3 -i $A/kesh.mp3 -i $A/whisper.mp3 \
  -filter_complex "\
[0:a]volume='if(between(t,47,52.5),0.45,0.85)':eval=frame[mus];\
[1:a]asplit=2[r1][r2];[r1]adelay=800|800,volume=0.6[ra];[r2]adelay=8200|8200,volume=0.45,afade=t=out:st=13.0:d=1.2[rb];\
[2:a]adelay=28200|28200,volume=0.8[bur];\
[3:a]adelay=46300|46300,volume=0.8[lan];\
[4:a]adelay=47500|47500,volume=1.3[kes];\
[5:a]adelay=88800|88800,volume=0.9[wh];\
[mus][ra][rb][bur][lan][kes][wh]amix=inputs=7:duration=first:normalize=0,alimiter=limit=0.95[out]" \
  -map "[out]" -c:a aac -b:a 160k build/ending_prani.m4a
