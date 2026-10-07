# Ilustrace cviků (docs/adr/0066)

Pózy a náčiní se ladí tady v Pythonu, náhled kreslí `engine.py` stejně jako `ExerciseFigureView`.

    pip install pillow
    python sheet.py arch.png                 # kontaktní arch všech cviků
    python sheet.py arch.png bench_press     # jen vybrané
    python gen.py                            # přegeneruje ExerciseFigureData.kt

Nový cvik: přidat `ex('id', start, end, props)` do `figs.py` (end = None → výdrž), zkontrolovat na archu, `gen.py`.
