package cz.uhk.macroflow.training.figure

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
        mapOf(
            "bench_press" to Illustration(
                Pose(pelvis = p(60f, 36f), torso = 180f, wrist = p(32f, 47f), ankle = p(78f, 2f)),
                Pose(pelvis = p(60f, 36f), torso = 180f, wrist = p(31f, 65f), ankle = p(78f, 2f)),
                listOf(
                Prop.Bar(p(8f, 27f), p(90f, 27f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(16f, 25f), p(16f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(82f, 25f), p(82f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(10f, 2f), p(22f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(76f, 2f), p(88f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "close_grip_bench" to Illustration(
                Pose(pelvis = p(60f, 36f), torso = 180f, wrist = p(32f, 47f), ankle = p(78f, 2f)),
                Pose(pelvis = p(60f, 36f), torso = 180f, wrist = p(31f, 65f), ankle = p(78f, 2f)),
                listOf(
                Prop.Bar(p(8f, 27f), p(90f, 27f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(16f, 25f), p(16f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(82f, 25f), p(82f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(10f, 2f), p(22f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(76f, 2f), p(88f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "skull_crusher" to Illustration(
                Pose(pelvis = p(60f, 36f), torso = 180f, wrist = p(14f, 48f), ankle = p(82f, 34f)),
                Pose(pelvis = p(60f, 36f), torso = 180f, wrist = p(29f, 65f), ankle = p(82f, 34f)),
                listOf(
                Prop.Bar(p(8f, 27f), p(90f, 27f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(16f, 25f), p(16f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(82f, 25f), p(82f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(10f, 2f), p(22f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(76f, 2f), p(88f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "incline_db_press" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 120f, wrist = p(33f, 59f), ankle = p(62f, 3f)),
                Pose(pelvis = p(40f, 30f), torso = 120f, wrist = p(48.4f, 69.5f), ankle = p(62f, 3f)),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(36f, 28f), p(20f, 56f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "chest_dip" to Illustration(
                Pose(pelvis = p(40f, 50f), torso = 75f, wrist = p(49f, 50f), ankle = p(23f, 38f), footAngle = 200f),
                Pose(pelvis = p(40f, 36f), torso = 65f, wrist = p(49f, 50f), ankle = p(23f, 24f), footAngle = 200f),
                listOf(
                Prop.Bar(p(36f, 0f), p(36f, 48f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(64f, 0f), p(64f, 48f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(32f, 48f), p(68f, 48f), 3f, Ink.FRAME, Layer.MID),
                Prop.Bar(p(20f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "cable_fly" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(74f, 82f), ankle = p(46f, 2f), view = View.FRONT),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(44f, 66f), ankle = p(46f, 2f), view = View.FRONT),
                listOf(
                Prop.Bar(p(84f, 0f), p(84f, 104f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(-4f, 0f), p(-4f, 104f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(82f, 100f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(82f, 100f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.Disc(p(-2f, 100f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(-2f, 100f), J.WRIST2, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.At(J.WRIST2, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(-8f, 0f), p(88f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "push_up" to Illustration(
                Pose(pelvis = p(32f, 24f), torso = 15f, wrist = p(61f, 2f), ankle = p(-13f, 7f), footAngle = -75f),
                Pose(pelvis = p(30f, 10f), torso = 12f, wrist = p(61f, 2f), ankle = p(-14f, 5f), footAngle = -75f),
                listOf(
                Prop.Bar(p(-24f, 0f), p(72f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "overhead_press" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(46f, 80f), ankle = p(41f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(41f, 108f), ankle = p(41f, 2f)),
                listOf(
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(62f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "db_shoulder_press" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 90f, wrist = p(45f, 64f), ankle = p(60f, 3f)),
                Pose(pelvis = p(40f, 30f), torso = 90f, wrist = p(41f, 89f), ankle = p(60f, 3f)),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(34f, 27f), p(34f, 64f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(72f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "machine_shoulder_press" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 90f, wrist = p(45f, 64f), ankle = p(60f, 3f)),
                Pose(pelvis = p(40f, 30f), torso = 90f, wrist = p(41f, 89f), ankle = p(60f, 3f)),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(34f, 27f), p(34f, 64f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(24f, 3f), p(24f, 40f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(24f, 40f), 2.5f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(24f, 40f), J.WRIST, 3f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(72f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "lateral_raise" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(50f, 50f), ankle = p(46f, 2f), elbowBend = 1, view = View.FRONT),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(76f, 78f), ankle = p(46f, 2f), elbowBend = 1, view = View.FRONT),
                listOf(
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.At(J.WRIST2, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(70f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "cable_lateral_raise" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(42f, 52f), ankle = p(46f, 2f), elbowBend = 1, view = View.FRONT, wrist2 = p(30f, 62f), elbowBend2 = -1),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(76f, 78f), ankle = p(46f, 2f), elbowBend = 1, view = View.FRONT, wrist2 = p(30f, 62f), elbowBend2 = -1),
                listOf(
                Prop.Bar(p(4f, 0f), p(4f, 40f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(6f, 8f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(6f, 8f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "front_raise" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(43f, 51f), ankle = p(41f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(68f, 80f), ankle = p(41f, 2f)),
                listOf(
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(74f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "pec_deck" to Illustration(
                Pose(pelvis = p(40f, 32f), torso = 90f, wrist = p(70f, 92f), ankle = p(48f, 3f), elbowBend = 1, view = View.FRONT, thighScale = 0.3f),
                Pose(pelvis = p(40f, 32f), torso = 90f, wrist = p(45f, 90f), ankle = p(48f, 3f), elbowBend = 1, view = View.FRONT, thighScale = 0.3f),
                listOf(
                Prop.Bar(p(40f, 30f), p(40f, 84f), 16f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(24f, 30f), p(56f, 30f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(4f, 0f), p(4f, 108f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(76f, 0f), p(76f, 108f), 5f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 3f, Ink.CUSHION, Layer.FRONT),
                Prop.At(J.WRIST2, 3f, Ink.CUSHION, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "machine_chest_press" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 92f, wrist = p(47f, 60f), ankle = p(60f, 3f)),
                Pose(pelvis = p(40f, 30f), torso = 92f, wrist = p(68f, 61f), ankle = p(60f, 3f)),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(35f, 27f), p(35f, 66f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(28f, 3f), p(30f, 100f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(30f, 98f), 2.5f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(30f, 98f), J.WRIST, 3f, Ink.STEEL, Layer.MID),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(76f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "incline_machine_press" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 115f, wrist = p(38f, 57f), ankle = p(62f, 7f)),
                Pose(pelvis = p(40f, 30f), torso = 115f, wrist = p(52f, 70f), ankle = p(62f, 7f)),
                listOf(
                Prop.Bar(p(6f, 3f), p(72f, 3f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(18f, 3f), p(24f, 96f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(38f, 3f), p(38f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(35f, 28f), p(21f, 60f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Disc(p(26f, 98f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(26f, 98f), J.WRIST, 3f, Ink.STEEL, Layer.MID),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT)
                )
            ),
            "triceps_pushdown" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 85f, wrist = p(52f, 72f), ankle = p(41f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 85f, wrist = p(44f, 49f), ankle = p(41f, 2f)),
                listOf(
                Prop.Bar(p(68f, 0f), p(68f, 108f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(68f, 108f), p(60f, 108f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(62f, 105f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(62f, 105f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(74f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "overhead_extension" to Illustration(
                Pose(pelvis = p(40f, 47f), torso = 75f, wrist = p(40f, 86f), ankle = p(50f, 2f), ankle2 = p(28f, 2f)),
                Pose(pelvis = p(40f, 47f), torso = 75f, wrist = p(73f, 95f), ankle = p(50f, 2f), ankle2 = p(28f, 2f)),
                listOf(
                Prop.Bar(p(2f, 0f), p(2f, 80f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(6f, 72f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(6f, 72f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(84f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "triceps_kickback" to Illustration(
                Pose(pelvis = p(34f, 46f), torso = 20f, wrist = p(47.2f, 36.8f), ankle = p(40f, 2f), elbowBend = 1, wrist2 = p(74f, 29f), ankle2 = p(26f, 2f), elbowBend2 = -1),
                Pose(pelvis = p(34f, 46f), torso = 20f, wrist = p(34.1f, 47.2f), ankle = p(40f, 2f), elbowBend = 1, wrist2 = p(74f, 29f), ankle2 = p(26f, 2f), elbowBend2 = -1),
                listOf(
                Prop.Bar(p(52f, 27f), p(96f, 27f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(60f, 25f), p(60f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(88f, 25f), p(88f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(54f, 2f), p(66f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(82f, 2f), p(94f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(98f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "face_pull" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(69f, 82f), ankle = p(41f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(44f, 86f), ankle = p(41f, 2f), elbowBend = 1),
                listOf(
                Prop.Bar(p(94f, 0f), p(94f, 100f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(90f, 86f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(90f, 86f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(98f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "reverse_fly" to Illustration(
                Pose(pelvis = p(36f, 46f), torso = 15f, wrist = p(66f, 24.8f), ankle = p(42f, 2f)),
                Pose(pelvis = p(36f, 46f), torso = 15f, wrist = p(64f, 62.8f), ankle = p(42f, 2f), elbowBend = 1, armScale = 0.35f),
                listOf(
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "barbell_curl" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(43f, 51f), ankle = p(41f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(47f, 77f), ankle = p(41f, 2f)),
                listOf(
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(66f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "ez_bar_curl" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(43f, 51f), ankle = p(41f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(47f, 77f), ankle = p(41f, 2f)),
                listOf(
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(66f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "hammer_curl" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(43f, 51f), ankle = p(41f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(47f, 77f), ankle = p(41f, 2f)),
                listOf(
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(66f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "incline_db_curl" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 125f, wrist = p(22.8f, 25.6f), ankle = p(62f, 3f)),
                Pose(pelvis = p(40f, 30f), torso = 125f, wrist = p(31.8f, 49.6f), ankle = p(62f, 3f)),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(36f, 28f), p(16f, 57f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "preacher_curl" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 85f, wrist = p(63.9f, 38.9f), ankle = p(62f, 3f), elbowBend = 1),
                Pose(pelvis = p(40f, 30f), torso = 85f, wrist = p(50.1f, 62.8f), ankle = p(62f, 3f), elbowBend = 1),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(44.9f, 55.6f), p(55.9f, 45.6f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(56.9f, 43.6f), p(60.9f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.ToJoint(p(54.9f, 48.6f), J.WRIST, 3f, Ink.STEEL, Layer.MID),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "single_arm_supported_curl" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 85f, wrist = p(63.9f, 38.9f), ankle = p(62f, 3f), elbowBend = 1),
                Pose(pelvis = p(40f, 30f), torso = 85f, wrist = p(50.1f, 62.8f), ankle = p(62f, 3f), elbowBend = 1),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(44.9f, 55.6f), p(55.9f, 45.6f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(56.9f, 43.6f), p(60.9f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "wrist_curl" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 70f, wrist = p(64f, 35f), ankle = p(64f, 3f)),
                Pose(pelvis = p(40f, 30f), torso = 70f, wrist = p(64f, 35f), ankle = p(64f, 3f)),
                listOf(
                Prop.Bar(p(14f, 25f), p(58f, 25f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(22f, 23f), p(22f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(50f, 23f), p(50f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(16f, 2f), p(28f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(44f, 2f), p(56f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Only(0, Prop.ToJoint(p(69f, 28f), J.WRIST, 3.6f, Ink.SKIN, Layer.FRONT)),
                Prop.Only(1, Prop.ToJoint(p(68f, 39f), J.WRIST, 3.6f, Ink.SKIN, Layer.FRONT)),
                Prop.Only(0, Prop.Disc(p(70f, 27f), 5f, Ink.PLATE, Layer.FRONT)),
                Prop.Only(1, Prop.Disc(p(69f, 40f), 5f, Ink.PLATE, Layer.FRONT)),
                Prop.Bar(p(0f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "reverse_wrist_curl" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 70f, wrist = p(64f, 35f), ankle = p(64f, 3f)),
                Pose(pelvis = p(40f, 30f), torso = 70f, wrist = p(64f, 35f), ankle = p(64f, 3f)),
                listOf(
                Prop.Bar(p(14f, 25f), p(58f, 25f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(22f, 23f), p(22f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(50f, 23f), p(50f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(16f, 2f), p(28f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(44f, 2f), p(56f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Only(0, Prop.ToJoint(p(69f, 28f), J.WRIST, 3.6f, Ink.SKIN, Layer.FRONT)),
                Prop.Only(1, Prop.ToJoint(p(68f, 39f), J.WRIST, 3.6f, Ink.SKIN, Layer.FRONT)),
                Prop.Only(0, Prop.Disc(p(70f, 27f), 3.6f, Ink.PLATE, Layer.FRONT)),
                Prop.Only(1, Prop.Disc(p(69f, 40f), 3.6f, Ink.PLATE, Layer.FRONT)),
                Prop.Bar(p(0f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "farmers_walk" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(41f, 50f), ankle = p(52f, 3f), ankle2 = p(28f, 3f)),
                Pose(pelvis = p(44f, 49f), torso = 90f, wrist = p(45f, 50f), ankle = p(32f, 3f), ankle2 = p(56f, 3f)),
                listOf(
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.At(J.WRIST, 4.2f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(14f, 0f), p(70f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "shrug" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(41f, 50f), ankle = p(41f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(41f, 53.5f), ankle = p(41f, 2f), shrug = 3.5f),
                listOf(
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(62f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "pull_up" to Illustration(
                Pose(pelvis = p(40f, 59f), torso = 90f, wrist = p(41f, 117f), ankle = p(36f, 14f), elbowBend = 1, kneeBend = -1),
                Pose(pelvis = p(40f, 80f), torso = 90f, wrist = p(41f, 117f), ankle = p(36f, 35f), elbowBend = 1, kneeBend = -1, armScale = 0.75f),
                listOf(
                Prop.Bar(p(41f, 118f), p(80f, 118f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(80f, 118f), p(80f, 0f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(41f, 118f), 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(90f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "chin_up" to Illustration(
                Pose(pelvis = p(40f, 59f), torso = 90f, wrist = p(41f, 117f), ankle = p(36f, 14f), elbowBend = 1, kneeBend = -1),
                Pose(pelvis = p(40f, 80f), torso = 90f, wrist = p(43f, 116f), ankle = p(36f, 35f), kneeBend = -1, armScale = 0.75f),
                listOf(
                Prop.Bar(p(41f, 118f), p(80f, 118f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(80f, 118f), p(80f, 0f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(41f, 118f), 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(90f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "lat_pulldown" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 95f, wrist = p(41f, 93f), ankle = p(64f, 6f), elbowBend = 1),
                Pose(pelvis = p(40f, 30f), torso = 100f, wrist = p(40f, 57f), ankle = p(64f, 6f)),
                listOf(
                Prop.Bar(p(28f, 3f), p(86f, 3f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(82f, 3f), p(82f, 114f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(82f, 114f), p(38f, 114f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(40f, 111f), 3.2f, Ink.STEEL, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(30f, 25f), p(50f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(70f, 38f), p(82f, 38f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(58f, 38f), p(70f, 38f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.ToJoint(p(40f, 108f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT)
                )
            ),
            "close_grip_pulldown" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 95f, wrist = p(42f, 92f), ankle = p(64f, 6f), elbowBend = 1),
                Pose(pelvis = p(40f, 30f), torso = 102f, wrist = p(42f, 58f), ankle = p(64f, 6f)),
                listOf(
                Prop.Bar(p(28f, 3f), p(86f, 3f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(82f, 3f), p(82f, 114f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(82f, 114f), p(38f, 114f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(40f, 111f), 3.2f, Ink.STEEL, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(30f, 25f), p(50f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(70f, 38f), p(82f, 38f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(58f, 38f), p(70f, 38f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.ToJoint(p(40f, 108f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT)
                )
            ),
            "barbell_row" to Illustration(
                Pose(pelvis = p(34f, 46f), torso = 25f, wrist = p(62.2f, 29.7f), ankle = p(40f, 2f)),
                Pose(pelvis = p(34f, 46f), torso = 25f, wrist = p(52f, 44f), ankle = p(40f, 2f)),
                listOf(
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "db_row" to Illustration(
                Pose(pelvis = p(30f, 46f), torso = 15f, wrist = p(60f, 25.8f), ankle = p(36f, 2f), wrist2 = p(72f, 29f), ankle2 = p(22f, 2f)),
                Pose(pelvis = p(30f, 46f), torso = 15f, wrist = p(48f, 46f), ankle = p(36f, 2f), wrist2 = p(72f, 29f), ankle2 = p(22f, 2f)),
                listOf(
                Prop.Bar(p(50f, 27f), p(94f, 27f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(58f, 25f), p(58f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(86f, 25f), p(86f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(52f, 2f), p(64f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(80f, 2f), p(92f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(98f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "straight_arm_pulldown" to Illustration(
                Pose(pelvis = p(40f, 47f), torso = 75f, wrist = p(63f, 101f), ankle = p(42f, 2f)),
                Pose(pelvis = p(40f, 47f), torso = 75f, wrist = p(50f, 47f), ankle = p(42f, 2f)),
                listOf(
                Prop.Bar(p(92f, 0f), p(92f, 112f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(92f, 112f), p(84f, 112f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(86f, 109f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(86f, 109f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "machine_row" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 75f, wrist = p(76f, 58f), ankle = p(60f, 3f)),
                Pose(pelvis = p(40f, 30f), torso = 75f, wrist = p(52f, 55f), ankle = p(60f, 3f)),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(58f, 36f), p(66f, 64f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(66f, 64f), p(84f, 94f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(84f, 94f), p(84f, 0f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(84f, 94f), 2.5f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(84f, 94f), J.WRIST, 3f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(90f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "seated_cable_row" to Illustration(
                Pose(pelvis = p(30f, 14f), torso = 82f, wrist = p(60f, 40f), ankle = p(72f, 14f)),
                Pose(pelvis = p(30f, 14f), torso = 96f, wrist = p(38f, 32f), ankle = p(72f, 14f)),
                listOf(
                Prop.Bar(p(6f, 8f), p(80f, 8f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(80f, 3f), p(80f, 26f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(78f, 4f), p(78f, 24f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(82f, 32f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(82f, 32f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(90f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "wide_cable_row" to Illustration(
                Pose(pelvis = p(30f, 14f), torso = 82f, wrist = p(60f, 42f), ankle = p(72f, 14f)),
                Pose(pelvis = p(30f, 14f), torso = 96f, wrist = p(36f, 40f), ankle = p(72f, 14f)),
                listOf(
                Prop.Bar(p(6f, 8f), p(80f, 8f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(80f, 3f), p(80f, 26f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(78f, 4f), p(78f, 24f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(82f, 32f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(82f, 32f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(90f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "reverse_pec_deck" to Illustration(
                Pose(pelvis = p(40f, 32f), torso = 90f, wrist = p(45f, 79f), ankle = p(48f, 3f), elbowBend = 1, view = View.FRONT, armScale = 0.35f, thighScale = 0.3f),
                Pose(pelvis = p(40f, 32f), torso = 90f, wrist = p(76f, 82f), ankle = p(48f, 3f), elbowBend = 1, view = View.FRONT, thighScale = 0.3f),
                listOf(
                Prop.Bar(p(40f, 30f), p(40f, 84f), 16f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(24f, 30f), p(56f, 30f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(4f, 0f), p(4f, 108f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(76f, 0f), p(76f, 108f), 5f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.At(J.WRIST2, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "plank" to Illustration(
                Pose(pelvis = p(30.3f, 13.3f), torso = 8f, wrist = p(74f, 3f), ankle = p(-14f, 5f), footAngle = -70f),
                null,
                listOf(
                Prop.Bar(p(-24f, 0f), p(84f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "cable_crunch" to Illustration(
                Pose(pelvis = p(40f, 27f), torso = 80f, wrist = p(50f, 64f), ankle = p(17f, 3f), footAngle = 180f),
                Pose(pelvis = p(40f, 27f), torso = 25f, wrist = p(72f, 44f), ankle = p(17f, 3f), footAngle = 180f),
                listOf(
                Prop.Bar(p(92f, 0f), p(92f, 112f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(92f, 112f), p(84f, 112f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(86f, 108f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(86f, 108f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "hanging_leg_raise" to Illustration(
                Pose(pelvis = p(40f, 60f), torso = 90f, wrist = p(41f, 119f), ankle = p(41f, 14f), elbowBend = 1),
                Pose(pelvis = p(40f, 60f), torso = 90f, wrist = p(41f, 119f), ankle = p(86f, 60f), elbowBend = 1),
                listOf(
                Prop.Bar(p(41f, 120f), p(80f, 120f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(80f, 120f), p(80f, 0f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(41f, 120f), 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(92f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "ab_wheel" to Illustration(
                Pose(pelvis = p(32f, 25f), torso = 40f, wrist = p(57f, 10f), ankle = p(-0.6f, 3f), footAngle = 180f),
                Pose(pelvis = p(44f, 14f), torso = 8f, wrist = p(100f, 8f), ankle = p(-0.3f, 3f), footAngle = 180f),
                listOf(
                Prop.At(J.WRIST, 4.5f, Ink.FRAME, Layer.FRONT),
                Prop.At(J.WRIST, 1.6f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(-8f, 0f), p(108f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "pallof_press" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(47f, 72f), ankle = p(48f, 2f), ankle2 = p(34f, 2f)),
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(68f, 74f), ankle = p(48f, 2f), ankle2 = p(34f, 2f)),
                listOf(
                Prop.Bar(p(20f, 0f), p(20f, 80f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(20f, 74f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(20f, 74f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "side_plank" to Illustration(
                Pose(pelvis = p(30f, 14f), torso = 15f, wrist = p(63f, 2f), ankle = p(-14f, 4f), elbowBend = 1, view = View.FRONT, wrist2 = p(58f, 50f), ankle2 = p(-14f, 9f), elbowBend2 = 1, kneeBend2 = 1, armScale = 0.45f, armScale2 = 1f),
                null,
                listOf(
                Prop.Bar(p(-24f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "woodchop" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 95f, wrist = p(70f, 100f), ankle = p(48f, 2f), elbowBend = 1, view = View.FRONT, wrist2 = p(70f, 98f), elbowBend2 = -1),
                Pose(pelvis = p(40f, 49f), torso = 85f, wrist = p(22f, 54f), ankle = p(48f, 2f), elbowBend = 1, view = View.FRONT, wrist2 = p(20f, 56f), elbowBend2 = -1),
                listOf(
                Prop.Bar(p(90f, 0f), p(90f, 112f), 5f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(88f, 106f), 3f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(88f, 106f), J.WRIST, 1.2f, Ink.STEEL, Layer.BACK),
                Prop.At(J.WRIST, 2.2f, Ink.FRAME, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "deadlift" to Illustration(
                Pose(pelvis = p(30f, 27f), torso = 25f, wrist = p(57f, 10f), ankle = p(54f, 2f)),
                Pose(pelvis = p(52f, 49f), torso = 90f, wrist = p(53f, 50f), ankle = p(54f, 2f)),
                listOf(
                Prop.At(J.WRIST, 10f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(30f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "rdl" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(41f, 50f), ankle = p(44f, 2f)),
                Pose(pelvis = p(34f, 46f), torso = 20f, wrist = p(61f, 28f), ankle = p(44f, 2f)),
                listOf(
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "back_extension" to Illustration(
                Pose(pelvis = p(52f, 52f), torso = -75f, wrist = p(50.3f, 27.7f), ankle = p(22f, 16f), kneeBend = -1, footAngle = -45f),
                Pose(pelvis = p(52f, 52f), torso = 50f, wrist = p(72.9f, 64.5f), ankle = p(22f, 16f), kneeBend = -1, footAngle = -45f),
                listOf(
                Prop.Bar(p(10f, 0f), p(72f, 0f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(20f, 14f), p(52f, 44f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(20f, 14f), p(16f, 0f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(56f, 40f), p(60f, 0f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(52f, 47f), p(62f, 37f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Disc(p(27f, 21f), 3.2f, Ink.CUSHION, Layer.FRONT)
                )
            ),
            "bird_dog" to Illustration(
                Pose(pelvis = p(30f, 27f), torso = 5f, wrist = p(60f, 2f), ankle = p(7f, 3f), footAngle = 180f),
                Pose(pelvis = p(30f, 27f), torso = 5f, wrist = p(89f, 33f), ankle = p(7f, 3f), footAngle = 180f, wrist2 = p(60f, 2f), ankle2 = p(-16f, 29f), kneeBend2 = 1, footAngle2 = 180f),
                listOf(
                Prop.Bar(p(-24f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "hip_thrust" to Illustration(
                Pose(pelvis = p(40f, 12f), torso = 159f, wrist = p(42.7f, 19f), ankle = p(62f, 2f), elbowBend = 1),
                Pose(pelvis = p(42f, 23f), torso = 180f, wrist = p(42f, 30.5f), ankle = p(62f, 2f), elbowBend = 1),
                listOf(
                Prop.Bar(p(-12f, 20f), p(14f, 20f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(-6f, 18f), p(-6f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(8f, 18f), p(8f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(-16f, 0f), p(80f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "bulgarian_split_squat" to Illustration(
                Pose(pelvis = p(40f, 47f), torso = 85f, wrist = p(43f, 48f), ankle = p(58f, 2f), ankle2 = p(8f, 24f), kneeBend2 = -1, footAngle2 = 180f),
                Pose(pelvis = p(36f, 28f), torso = 82f, wrist = p(40f, 29f), ankle = p(58f, 2f), ankle2 = p(8f, 24f), kneeBend2 = -1, footAngle2 = 180f),
                listOf(
                Prop.Bar(p(-4f, 22f), p(16f, 22f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(6f, 20f), p(6f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(-8f, 0f), p(72f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "hip_abduction" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 90f, wrist = p(54f, 32f), ankle = p(45f, 3f), view = View.FRONT, thighScale = 0.3f),
                Pose(pelvis = p(40f, 30f), torso = 90f, wrist = p(54f, 32f), ankle = p(60f, 3f), view = View.FRONT, thighScale = 0.3f),
                listOf(
                Prop.Bar(p(40f, 30f), p(40f, 82f), 16f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(24f, 30f), p(56f, 30f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Pad(J.KNEE, p(4f, 5f), p(4f, -8f), 3f, Ink.CUSHION, Layer.FRONT),
                Prop.Pad(J.KNEE2, p(-4f, 5f), p(-4f, -8f), 3f, Ink.CUSHION, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(74f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "back_squat" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(36f, 78f), ankle = p(44f, 2f)),
                Pose(pelvis = p(30f, 26f), torso = 55f, wrist = p(43.2f, 49.6f), ankle = p(44f, 2f)),
                listOf(
                Prop.At(J.WRIST, 8.5f, Ink.PLATE, Layer.MID),
                Prop.At(J.WRIST, 2f, Ink.STEEL, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(70f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "leg_press" to Illustration(
                Pose(pelvis = p(40f, 20f), torso = 135f, wrist = p(44f, 24f), ankle = p(55f, 40f), elbowBend = 1, footAngle = 45f),
                Pose(pelvis = p(40f, 20f), torso = 135f, wrist = p(44f, 24f), ankle = p(71f, 52f), elbowBend = 1, footAngle = 45f),
                listOf(
                Prop.Bar(p(0f, 2f), p(98f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(56f, 26f), p(96f, 66f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(96f, 66f), p(96f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(42f, 15f), p(16f, 41f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(34f, 14f), p(50f, 20f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(42f, 14f), p(42f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Pad(J.ANKLE, p(-3f, 9f), p(9f, -3f), 4f, Ink.FRAME, Layer.MID)
                )
            ),
            "single_leg_press" to Illustration(
                Pose(pelvis = p(40f, 20f), torso = 135f, wrist = p(44f, 24f), ankle = p(55f, 40f), elbowBend = 1, footAngle = 45f, ankle2 = p(54f, 8f), kneeBend2 = 1),
                Pose(pelvis = p(40f, 20f), torso = 135f, wrist = p(44f, 24f), ankle = p(71f, 52f), elbowBend = 1, footAngle = 45f, ankle2 = p(54f, 8f), kneeBend2 = 1),
                listOf(
                Prop.Bar(p(0f, 2f), p(98f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(56f, 26f), p(96f, 66f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(96f, 66f), p(96f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(42f, 15f), p(16f, 41f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(34f, 14f), p(50f, 20f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(42f, 14f), p(42f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Pad(J.ANKLE, p(-3f, 9f), p(9f, -3f), 4f, Ink.FRAME, Layer.MID)
                )
            ),
            "leg_extension" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 100f, wrist = p(44f, 28f), ankle = p(66f, 8f), elbowBend = 1),
                Pose(pelvis = p(40f, 30f), torso = 100f, wrist = p(44f, 28f), ankle = p(87f, 33f), elbowBend = 1),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(34f, 27f), p(30f, 64f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(64f, 31f), p(64f, 3f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(64f, 31f), 2.5f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(64f, 31f), J.ANKLE, 2.5f, Ink.STEEL, Layer.BACK),
                Prop.At(J.ANKLE, 3.2f, Ink.CUSHION, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "seated_leg_curl" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 100f, wrist = p(44f, 28f), ankle = p(87f, 33f), elbowBend = 1),
                Pose(pelvis = p(40f, 30f), torso = 100f, wrist = p(44f, 28f), ankle = p(58f, 10f), elbowBend = 1),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(34f, 27f), p(30f, 64f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(64f, 31f), p(64f, 3f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(64f, 31f), 2.5f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(64f, 31f), J.ANKLE, 2.5f, Ink.STEEL, Layer.BACK),
                Prop.At(J.ANKLE, 3.2f, Ink.CUSHION, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(96f, 0f), 2f, Ink.STEEL, Layer.BACK),
                Prop.Bar(p(56f, 38f), p(68f, 38f), 5f, Ink.CUSHION, Layer.FRONT)
                )
            ),
            "lying_leg_curl" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 0f, wrist = p(80f, 22f), ankle = p(-6f, 29f), kneeBend = -1, footAngle = 180f),
                Pose(pelvis = p(40f, 30f), torso = 0f, wrist = p(80f, 22f), ankle = p(28f, 52f), kneeBend = -1, footAngle = 150f),
                listOf(
                Prop.Bar(p(8f, 24f), p(84f, 24f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(20f, 22f), p(20f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(72f, 22f), p(72f, 3f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(6f, 2f), p(86f, 2f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Disc(p(14f, 26f), 2.5f, Ink.STEEL, Layer.BACK),
                Prop.ToJoint(p(14f, 26f), J.ANKLE, 2.5f, Ink.STEEL, Layer.BACK),
                Prop.At(J.ANKLE, 3.2f, Ink.CUSHION, Layer.FRONT)
                )
            ),
            "nordic_curl" to Illustration(
                Pose(pelvis = p(30f, 27f), torso = 90f, wrist = p(38f, 50f), ankle = p(7f, 3f), footAngle = 180f),
                Pose(pelvis = p(48.4f, 18.4f), torso = 40f, wrist = p(80f, 8f), ankle = p(7f, 3f), footAngle = 180f),
                listOf(
                Prop.Bar(p(14f, 0f), p(46f, 0f), 3f, Ink.FRAME, Layer.BACK),
                Prop.At(J.ANKLE, 2.8f, Ink.CUSHION, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(90f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "walking_lunge" to Illustration(
                Pose(pelvis = p(40f, 49f), torso = 90f, wrist = p(41f, 50f), ankle = p(42f, 2f), ankle2 = p(39f, 2f)),
                Pose(pelvis = p(38f, 28f), torso = 88f, wrist = p(39f, 29f), ankle = p(60f, 2f), ankle2 = p(14f, 4f), kneeBend2 = 1, footAngle2 = -50f),
                listOf(
                Prop.At(J.WRIST, 3.6f, Ink.PLATE, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(74f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "hack_squat" to Illustration(
                Pose(pelvis = p(40f, 47f), torso = 100f, wrist = p(40f, 82f), ankle = p(48f, 6f), footAngle = 15f),
                Pose(pelvis = p(43.8f, 25.3f), torso = 100f, wrist = p(43f, 61f), ankle = p(48f, 6f), footAngle = 15f),
                listOf(
                Prop.Bar(p(26f, 3f), p(10f, 86f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(62f, 8f), 3f, Ink.FRAME, Layer.BACK),
                Prop.Pad(J.SHOULDER, p(-6.9f, -1.2f), p(-1.7f, -30.7f), 5f, Ink.CUSHION, Layer.BACK),
                Prop.Pad(J.SHOULDER, p(-3f, 4f), p(5f, 4f), 4f, Ink.CUSHION, Layer.FRONT),
                Prop.Bar(p(0f, 0f), p(70f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "standing_calf_raise" to Illustration(
                Pose(pelvis = p(40f, 51f), torso = 90f, wrist = p(44f, 84f), ankle = p(41f, 4f), footAngle = -10f),
                Pose(pelvis = p(40f, 56f), torso = 90f, wrist = p(44f, 89f), ankle = p(41f, 9f), footAngle = -50f),
                listOf(
                Prop.Bar(p(40f, 2f), p(60f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Pad(J.SHOULDER, p(-4f, 4f), p(6f, 4f), 4f, Ink.CUSHION, Layer.FRONT),
                Prop.Bar(p(20f, 0f), p(64f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            ),
            "seated_calf_raise" to Illustration(
                Pose(pelvis = p(40f, 30f), torso = 90f, wrist = p(62f, 38f), ankle = p(64f, 8f), footAngle = 12f),
                Pose(pelvis = p(40f, 33f), torso = 90f, wrist = p(62f, 41f), ankle = p(64f, 12f), footAngle = -30f),
                listOf(
                Prop.Bar(p(28f, 25f), p(52f, 25f), 6f, Ink.CUSHION, Layer.BACK),
                Prop.Bar(p(40f, 3f), p(40f, 24f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(26f, 2f), p(54f, 2f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Bar(p(64f, 4f), p(76f, 4f), 4f, Ink.FRAME, Layer.BACK),
                Prop.Pad(J.KNEE, p(-4f, 5f), p(6f, 5f), 4f, Ink.CUSHION, Layer.FRONT),
                Prop.Bar(p(10f, 0f), p(82f, 0f), 2f, Ink.STEEL, Layer.BACK)
                )
            )
        )
    }
}
