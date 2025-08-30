package com.immagineran.no

/**
 * Container for data moving through the processing pipeline.
 */
data class ProcessingContext(
    val prompt: String,
    val segments: List<String>,
    var story: String? = null,
)

/**
 * A single step in a processing pipeline.
 */
fun interface ProcessingStep {
    suspend fun process(context: ProcessingContext)
}

/**
 * Simple pipeline executing a list of [ProcessingStep]s sequentially.
 */
class ProcessingPipeline(private val steps: List<ProcessingStep>) {
    suspend fun run(context: ProcessingContext): ProcessingContext {
        steps.forEach { it.process(context) }
        return context
    }
}

/**
 * Step that stitches a list of transcribed segments into a story.
 */
class StoryStitchingStep(
    private val stitcher: StoryStitcher = StoryStitcher(),
) : ProcessingStep {
    override suspend fun process(context: ProcessingContext) {
        context.story = stitcher.stitch(context.prompt, context.segments)
    }
}

