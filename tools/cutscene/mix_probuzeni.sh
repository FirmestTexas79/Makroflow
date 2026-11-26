#!/bin/sh
# Zvuk cutscény konce IV (ElevenLabs: hudba, svítání, jmenovka, Hunfrey, složka, dveře, automat) → build/ending_probuzeni.m4a.
# Hudba vyšla na 86 s → zpomalená na 0,9× (≈ 96 s), aby části seděly se záběry. Časy sedí s ending_wake.py.
A=${1:-audio/probuzeni}
ffmpeg -y -loglevel error \
  -i $A/music.mp3 -i $A/dawn.mp3 -i $A/flip.mp3 -i $A/hunfrey.mp3 -i $A/pages.mp3 -i $A/door.mp3 -i $A/ticket.mp3 \
  -filter_complex "\
[0:a]atempo=0.9,apad=whole_dur=96,volume='if(between(t,31.8,36.2),0.4,0.85)':eval=frame,afade=t=out:st=93:d=3[mus];\
[1:a]adelay=300|300,volume=0.6,afade=t=out:st=6.5:d=1.8[dawn];\
[2:a]adelay=14000|14000,volume=0.7[flip];\
[3:a]adelay=32300|32300,volume=1.4[hun];\
[4:a]adelay=53300|53300,volume=0.7[pg];\
[5:a]adelay=67800|67800,volume=0.7[door];\
[6:a]adelay=81600|81600,volume=0.75[tik];\
[mus][dawn][flip][hun][pg][door][tik]amix=inputs=7:duration=first:normalize=0,alimiter=limit=0.95[out]" \
  -map "[out]" -c:a aac -b:a 160k build/ending_probuzeni.m4a
