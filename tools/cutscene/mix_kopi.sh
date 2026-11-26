#!/bin/sh
# Zvuk cutscény konce I (ElevenLabs: hudba, Septův hlas, efekty) → build/ending_kopi.m4a. Časy sedí s ending_spear.py.
A=${1:-audio/kopi}
ffmpeg -y -loglevel error \
  -i $A/music.mp3 -i $A/release.mp3 -i $A/ash.mp3 -i $A/radio.mp3 -i $A/sept1.mp3 -i $A/sept2.mp3 -i $A/flap.mp3 \
  -filter_complex "\
[0:a]volume=0.9,volume='if(between(t,53.5,63),0.55,1)':eval=frame[mus];\
[1:a]asplit=3[r1][r2][r3];\
[r1]adelay=27300|27300,volume=0.8[ra];[r2]adelay=30700|30700,volume=0.8[rb];[r3]adelay=34100|34100,volume=0.9[rc];\
[2:a]adelay=37500|37500,volume=0.9[ash];\
[3:a]adelay=53300|53300,volume=0.35[rad];\
[4:a]highpass=f=320,lowpass=f=3200,acrusher=bits=10:mix=0.25,adelay=54100|54100,volume=1.6[s1];\
[5:a]highpass=f=320,lowpass=f=3200,acrusher=bits=10:mix=0.25,adelay=58700|58700,volume=1.6[s2];\
[6:a]adelay=72500|72500,volume=0.7[flap];\
[mus][ra][rb][rc][ash][rad][s1][s2][flap]amix=inputs=9:duration=first:normalize=0,alimiter=limit=0.95[out]" \
  -map "[out]" -c:a aac -b:a 160k build/ending_kopi.m4a
