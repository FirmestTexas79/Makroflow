#!/bin/sh
# Zvuk cutscény konce II (ElevenLabs: hudba, voda, rašení, řeka, deštník) → build/ending_kveteni.m4a. Časy sedí s ending_bloom.py.
A=${1:-audio/kveteni}
ffmpeg -y -loglevel error \
  -i $A/music.mp3 -i $A/rise.mp3 -i $A/sprout.mp3 -i $A/umbrella.mp3 -i $A/river.mp3 \
  -filter_complex "\
[0:a]volume=0.85[mus];\
[1:a]adelay=800|800,volume=0.8[rise];\
[2:a]asplit=4[p1][p2][p3][p4];\
[p1]adelay=10500|10500,volume=0.6[sa];[p2]adelay=21000|21000,volume=0.55[sb];[p3]adelay=29000|29000,volume=0.5[sc];[p4]adelay=73500|73500,volume=0.6[sd];\
[3:a]adelay=39300|39300,volume=0.8[umb];\
[4:a]adelay=50500|50500,volume=0.7,afade=t=out:st=60.0:d=1.5[riv];\
[mus][rise][sa][sb][sc][sd][umb][riv]amix=inputs=8:duration=first:normalize=0,alimiter=limit=0.95[out]" \
  -map "[out]" -c:a aac -b:a 160k build/ending_kveteni.m4a
