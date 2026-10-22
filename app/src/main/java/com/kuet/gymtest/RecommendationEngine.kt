package com.kuet.gymtest

object RecommendationEngine {

    private const val NONE = "No Equipment"
    private const val DB = "Dumbbells"
    private const val MG = "Muscle Gain"
    private const val WL = "Weight Loss"

    private fun ex(
        name: String, goal: String, level: String, equipment: String,
        muscle: String, category: String, desc: String,
        sets: Int, reps: Int, lowImpact: Boolean,
        vararg steps: String
    ) = Exercise(
        name = name, goal = goal, level = level, equipment = equipment,
        muscleGroup = muscle, category = category, description = desc,
        instructions = steps.toList(), defaultSets = sets, defaultReps = reps,
        lowImpact = lowImpact
    )

    private val exercises = listOf(

        // ---------- MUSCLE GAIN: NO EQUIPMENT ----------
        ex("Bodyweight Squat", MG, "Beginner", NONE, "Legs", "Strength",
            "A fundamental lower-body exercise for quads, glutes and hamstrings.", 3, 12, true,
            "Stand with feet shoulder-width apart.",
            "Keep your chest up and back neutral.",
            "Lower your hips by bending your knees.",
            "Push through your feet to stand."),
        ex("Push-up", MG, "Beginner", NONE, "Chest", "Strength",
            "A bodyweight push targeting chest, shoulders and triceps.", 3, 10, false,
            "Place hands slightly wider than shoulders.",
            "Keep your body in a straight line.",
            "Lower your chest toward the floor.",
            "Push back up."),
        ex("Wall Push-up", MG, "Beginner", NONE, "Chest", "Strength",
            "A gentle push-up variation done standing against a wall.", 3, 12, true,
            "Stand an arm's length from a wall.",
            "Place your palms on the wall at chest height.",
            "Bend your elbows to bring your chest close.",
            "Push back to the start."),
        ex("Glute Bridge", MG, "Beginner", NONE, "Glutes", "Strength",
            "A lower-body exercise focused on the glutes.", 3, 15, true,
            "Lie on your back with knees bent.",
            "Keep feet flat on the floor.",
            "Raise your hips while squeezing your glutes.",
            "Lower under control."),
        ex("Plank", MG, "Beginner", NONE, "Core", "Core",
            "An isometric hold that builds core stability (reps = seconds).", 3, 30, true,
            "Place forearms on the floor.",
            "Extend your legs behind you.",
            "Keep your body in a straight line.",
            "Hold while breathing steadily."),
        ex("Superman", MG, "Beginner", NONE, "Back", "Strength",
            "A floor exercise strengthening the lower back and glutes.", 3, 12, true,
            "Lie face down with arms extended forward.",
            "Lift your arms, chest and legs slightly.",
            "Hold for one second.",
            "Lower slowly."),
        ex("Pike Push-up", MG, "Intermediate", NONE, "Shoulders", "Strength",
            "A push-up with hips raised to target the shoulders.", 3, 8, false,
            "Start in a downward-dog position.",
            "Bend your elbows to lower your head toward the floor.",
            "Press back up to the start."),
        ex("Split Squat", MG, "Intermediate", NONE, "Legs", "Strength",
            "A single-leg-focused squat that builds strength and balance.", 3, 10, true,
            "Step one foot forward into a long stance.",
            "Lower your back knee toward the floor.",
            "Drive through the front heel to rise."),
        ex("Diamond Push-up", MG, "Advanced", NONE, "Triceps", "Strength",
            "A close-hand push-up emphasizing the triceps.", 3, 8, false,
            "Form a diamond with your hands under your chest.",
            "Keep elbows close to your body.",
            "Lower and press back up."),
        ex("Pistol Squat", MG, "Advanced", NONE, "Legs", "Strength",
            "A single-leg squat requiring strength, balance and mobility.", 3, 5, false,
            "Stand on one leg with the other extended forward.",
            "Lower into a deep squat.",
            "Drive up through your heel."),

        // ---------- MUSCLE GAIN: DUMBBELLS ----------
        ex("Dumbbell Curl", MG, "Beginner", DB, "Biceps", "Strength",
            "An isolation exercise targeting the biceps.", 3, 12, true,
            "Stand holding a dumbbell in each hand.",
            "Keep elbows close to your body.",
            "Curl the weights to your shoulders.",
            "Lower slowly."),
        ex("Goblet Squat", MG, "Beginner", DB, "Legs", "Strength",
            "A squat holding one dumbbell at the chest.", 3, 12, true,
            "Hold a dumbbell vertically at your chest.",
            "Squat down keeping your chest tall.",
            "Stand back up."),
        ex("Dumbbell Shoulder Press", MG, "Intermediate", DB, "Shoulders", "Strength",
            "A pressing movement targeting the shoulders.", 3, 10, true,
            "Hold dumbbells at shoulder height.",
            "Brace your core.",
            "Press overhead, then lower slowly."),
        ex("Dumbbell Row", MG, "Intermediate", DB, "Back", "Strength",
            "A pulling exercise for the upper back and arms.", 3, 10, true,
            "Hold dumbbells and hinge forward with a neutral back.",
            "Pull the weights toward your torso.",
            "Lower under control."),
        ex("Dumbbell Floor Press", MG, "Intermediate", DB, "Chest", "Strength",
            "A chest press performed lying on the floor.", 3, 10, true,
            "Lie on your back holding dumbbells above your chest.",
            "Lower until your upper arms touch the floor.",
            "Press back up."),
        ex("Romanian Deadlift", MG, "Intermediate", DB, "Hamstrings", "Strength",
            "A hip-hinge targeting hamstrings and glutes.", 3, 10, true,
            "Hold dumbbells in front of your thighs.",
            "Push your hips back with a flat back.",
            "Stand tall by driving your hips forward."),
        ex("Dumbbell Lunge", MG, "Intermediate", DB, "Legs", "Strength",
            "A weighted lunge for legs and glutes.", 3, 10, true,
            "Hold dumbbells at your sides.",
            "Step forward and lower your back knee.",
            "Push back to the start."),
        ex("Dumbbell Thruster", MG, "Advanced", DB, "Full Body", "Strength",
            "A squat into an overhead press in one movement.", 3, 8, false,
            "Hold dumbbells at your shoulders.",
            "Squat down, then drive up.",
            "Press the weights overhead as you stand."),
        ex("Renegade Row", MG, "Advanced", DB, "Back", "Strength",
            "A plank-position row that challenges back and core.", 3, 8, false,
            "Start in a push-up position on the dumbbells.",
            "Row one dumbbell to your hip.",
            "Lower and alternate sides."),

        // ---------- WEIGHT LOSS: NO EQUIPMENT ----------
        ex("Marching in Place", WL, "Beginner", NONE, "Full Body", "Cardio",
            "A low-impact cardio warm-up that raises your heart rate.", 3, 40, true,
            "Stand tall.",
            "Lift your knees alternately at a steady pace.",
            "Swing your arms naturally."),
        ex("Shadow Boxing", WL, "Beginner", NONE, "Full Body", "Cardio",
            "Light punching combos for low-impact cardio.", 3, 30, true,
            "Stand in a staggered stance.",
            "Throw alternating punches.",
            "Keep your core tight and move your feet lightly."),
        ex("Jumping Jacks", WL, "Beginner", NONE, "Full Body", "Cardio",
            "A simple full-body cardio exercise.", 3, 20, false,
            "Stand with feet together.",
            "Jump feet apart while raising arms overhead.",
            "Return to the start."),
        ex("High Knees", WL, "Beginner", NONE, "Full Body", "Cardio",
            "Alternating high knee drives to raise your heart rate.", 3, 20, false,
            "Stand upright.",
            "Drive one knee toward your chest.",
            "Alternate quickly and stay light on your feet."),
        ex("Lunges", WL, "Beginner", NONE, "Legs", "Cardio",
            "A lower-body movement that challenges balance.", 3, 10, true,
            "Stand with feet together.",
            "Step forward and lower your body.",
            "Push through the front foot to return."),
        ex("Mountain Climbers", WL, "Intermediate", NONE, "Core", "Cardio",
            "A plank-position cardio move.", 3, 20, false,
            "Start in a high plank.",
            "Drive your knees toward your chest alternately.",
            "Keep your hips low."),
        ex("Burpees", WL, "Intermediate", NONE, "Full Body", "Cardio",
            "A high-intensity full-body movement.", 3, 8, false,
            "Squat and place hands on the floor.",
            "Jump your feet back to a plank.",
            "Jump feet forward and leap up."),
        ex("Squat Jumps", WL, "Advanced", NONE, "Legs", "Cardio",
            "An explosive squat that boosts heart rate.", 3, 12, false,
            "Lower into a squat.",
            "Explode upward into a jump.",
            "Land softly and repeat."),
        ex("Tuck Jumps", WL, "Advanced", NONE, "Full Body", "Cardio",
            "A powerful jump bringing knees toward the chest.", 3, 10, false,
            "Stand with feet hip-width apart.",
            "Jump and pull your knees up.",
            "Land softly."),

        // ---------- WEIGHT LOSS: DUMBBELLS ----------
        ex("Dumbbell Squat to Press", WL, "Beginner", DB, "Full Body", "Cardio",
            "A light full-body combo to keep your heart rate up.", 3, 12, true,
            "Hold dumbbells at your shoulders.",
            "Squat down.",
            "Stand and press the weights overhead."),
        ex("Dumbbell Swing", WL, "Advanced", DB, "Full Body", "Cardio",
            "A powerful hip-driven swing with one dumbbell.", 3, 15, false,
            "Hold one dumbbell with both hands.",
            "Hinge and swing it between your legs.",
            "Drive your hips forward to swing it to chest height.")
    )

    private fun rank(level: String) = when (level) {
        "Beginner" -> 0
        "Intermediate" -> 1
        "Advanced" -> 2
        else -> 0
    }

    fun recommend(profile: UserProfile): List<Exercise> {
        val userLevel = rank(profile.fitnessLevel)

        val eligible = exercises.filter {
            it.goal == profile.goal &&
                    rank(it.level) <= userLevel &&
                    (it.equipment == NONE || it.equipment == profile.equipment) &&
                    (profile.age < 50 || it.lowImpact)
        }

        val ranked = eligible.sortedWith(
            compareByDescending<Exercise> { rank(it.level) }
                .thenByDescending { it.equipment == profile.equipment && it.equipment != NONE }
        )

        return interleave(ranked).take(8)
    }

    // Mix muscle groups so the list isn't all legs, then all chest, etc.
    private fun interleave(list: List<Exercise>): List<Exercise> {
        val groups = list.groupBy { it.muscleGroup }.values.map { it.toMutableList() }
        val out = mutableListOf<Exercise>()
        while (groups.any { it.isNotEmpty() }) {
            groups.forEach { if (it.isNotEmpty()) out += it.removeAt(0) }
        }
        return out
    }

    fun getAllExercises(): List<Exercise> = exercises
}