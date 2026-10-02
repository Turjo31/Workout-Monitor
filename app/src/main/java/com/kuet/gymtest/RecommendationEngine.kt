package com.kuet.gymtest

object RecommendationEngine {

    private val exercises = listOf(

        // =========================
        // MUSCLE GAIN - BEGINNER
        // =========================

        Exercise(
            name = "Bodyweight Squat",
            goal = "Muscle Gain",
            level = "Beginner",
            equipment = "No Equipment",
            muscleGroup = "Legs",
            category = "Strength",
            description = "A fundamental lower-body exercise targeting the quadriceps, glutes and hamstrings.",
            instructions = listOf(
                "Stand with your feet approximately shoulder-width apart.",
                "Keep your chest up and your back neutral.",
                "Bend your knees and lower your hips.",
                "Push through your feet to return to the starting position."
            ),
            defaultSets = 3,
            defaultReps = 12
        ),

        Exercise(
            name = "Push-up",
            goal = "Muscle Gain",
            level = "Beginner",
            equipment = "No Equipment",
            muscleGroup = "Chest",
            category = "Strength",
            description = "A bodyweight pushing exercise targeting the chest, shoulders and triceps.",
            instructions = listOf(
                "Place your hands slightly wider than shoulder width.",
                "Keep your body in a straight line.",
                "Lower your chest toward the floor.",
                "Push back up while keeping your body straight."
            ),
            defaultSets = 3,
            defaultReps = 10
        ),

        Exercise(
            name = "Glute Bridge",
            goal = "Muscle Gain",
            level = "Beginner",
            equipment = "No Equipment",
            muscleGroup = "Glutes",
            category = "Strength",
            description = "A lower-body exercise focused primarily on the glutes.",
            instructions = listOf(
                "Lie on your back with your knees bent.",
                "Keep your feet flat on the floor.",
                "Raise your hips while squeezing your glutes.",
                "Lower your hips under control."
            ),
            defaultSets = 3,
            defaultReps = 15
        ),

        Exercise(
            name = "Plank",
            goal = "Muscle Gain",
            level = "Beginner",
            equipment = "No Equipment",
            muscleGroup = "Core",
            category = "Core",
            description = "An isometric exercise that develops core stability.",
            instructions = listOf(
                "Place your forearms on the floor.",
                "Extend your legs behind you.",
                "Keep your body in a straight line.",
                "Hold the position while maintaining controlled breathing."
            ),
            defaultSets = 3,
            defaultReps = 30
        ),

        // =========================
        // MUSCLE GAIN - DUMBBELLS
        // =========================

        Exercise(
            name = "Dumbbell Curl",
            goal = "Muscle Gain",
            level = "Beginner",
            equipment = "Dumbbells",
            muscleGroup = "Biceps",
            category = "Strength",
            description = "An isolation exercise targeting the biceps.",
            instructions = listOf(
                "Stand upright while holding a dumbbell in each hand.",
                "Keep your elbows close to your body.",
                "Curl the weights toward your shoulders.",
                "Lower the weights slowly."
            ),
            defaultSets = 3,
            defaultReps = 12
        ),

        Exercise(
            name = "Dumbbell Shoulder Press",
            goal = "Muscle Gain",
            level = "Intermediate",
            equipment = "Dumbbells",
            muscleGroup = "Shoulders",
            category = "Strength",
            description = "A pressing movement that primarily targets the shoulders.",
            instructions = listOf(
                "Hold the dumbbells at shoulder height.",
                "Keep your core stable.",
                "Press the dumbbells overhead.",
                "Lower them slowly back to shoulder height."
            ),
            defaultSets = 3,
            defaultReps = 10
        ),

        Exercise(
            name = "Dumbbell Row",
            goal = "Muscle Gain",
            level = "Intermediate",
            equipment = "Dumbbells",
            muscleGroup = "Back",
            category = "Strength",
            description = "A pulling exercise that targets the upper back and arms.",
            instructions = listOf(
                "Hold a dumbbell in each hand.",
                "Hinge forward while keeping your back neutral.",
                "Pull the dumbbells toward your torso.",
                "Lower them under control."
            ),
            defaultSets = 3,
            defaultReps = 10
        ),

        // =========================
        // WEIGHT LOSS
        // =========================

        Exercise(
            name = "Jumping Jacks",
            goal = "Weight Loss",
            level = "Beginner",
            equipment = "No Equipment",
            muscleGroup = "Full Body",
            category = "Cardio",
            description = "A simple full-body cardio exercise that increases heart rate.",
            instructions = listOf(
                "Stand upright with your feet together.",
                "Jump while moving your feet apart.",
                "Raise your arms overhead.",
                "Return to the starting position."
            ),
            defaultSets = 3,
            defaultReps = 20
        ),

        Exercise(
            name = "High Knees",
            goal = "Weight Loss",
            level = "Beginner",
            equipment = "No Equipment",
            muscleGroup = "Full Body",
            category = "Cardio",
            description = "A cardio movement involving alternating high knee drives.",
            instructions = listOf(
                "Stand upright.",
                "Drive one knee toward your chest.",
                "Lower it while raising the opposite knee.",
                "Continue alternating at a controlled pace."
            ),
            defaultSets = 3,
            defaultReps = 20
        ),

        Exercise(
            name = "Lunges",
            goal = "Weight Loss",
            level = "Beginner",
            equipment = "No Equipment",
            muscleGroup = "Legs",
            category = "Cardio",
            description = "A lower-body movement that also challenges balance and coordination.",
            instructions = listOf(
                "Stand upright with your feet together.",
                "Step forward with one leg.",
                "Lower your body under control.",
                "Push through the front foot to return."
            ),
            defaultSets = 3,
            defaultReps = 10
        )
    )

    fun recommend(profile: UserProfile): List<Exercise> {

        return exercises.filter { exercise ->

            exercise.goal == profile.goal &&
                    exercise.level == profile.fitnessLevel &&
                    exercise.equipment == profile.equipment
        }
    }

    fun getAllExercises(): List<Exercise> {
        return exercises
    }
}