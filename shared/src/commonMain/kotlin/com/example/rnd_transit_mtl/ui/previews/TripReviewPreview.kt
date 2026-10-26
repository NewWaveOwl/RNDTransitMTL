package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.model.TripReviewMode
import com.example.rnd_transit_mtl.state.ReviewDraft
import com.example.rnd_transit_mtl.state.ReviewStep
import com.example.rnd_transit_mtl.ui.TripReviewContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

/**
 * Fixed form values only.
 *
 * These previews do not create a store, navigate, initialize drafts,
 * or automatically advance questions.
 */
@Preview(
    name = "Review — unrated Overall",
    showBackground = true,
    widthDp = 320,
    heightDp = 900
)
@Composable
fun TripReviewUnratedPreview() {
    ReviewExample(draft = ReviewDraft())
}

@Preview(
    name = "Review — three stars and comment",
    showBackground = true,
    widthDp = 390,
    heightDp = 900
)
@Composable
fun TripReviewOverallPreview() {
    ReviewExample(
        draft = ReviewDraft(
            overall = 3,
            comment = "I enjoyed the relaxed pace.",
            imageUrl = "https://example.com/trip-photo.jpg"
        )
    )
}

@Preview(
    name = "Review — optional Quality",
    showBackground = true,
    widthDp = 320,
    heightDp = 800
)
@Composable
fun TripReviewQualityPreview() {
    ReviewExample(
        draft = ReviewDraft(
            overall = 4,
            step = ReviewStep.QUALITY
        )
    )
}

@Preview(
    name = "Review — How interesting",
    showBackground = true,
    widthDp = 390,
    heightDp = 800
)
@Composable
fun TripReviewInterestingPreview() {
    ReviewExample(
        draft = ReviewDraft(
            overall = 4,
            interesting = 5,
            step = ReviewStep.INTERESTING
        )
    )
}

@Preview(
    name = "Review — edit Fun on Desktop",
    showBackground = true,
    widthDp = 1000,
    heightDp = 850
)
@Composable
fun TripReviewEditPreview() {
    ReviewExample(
        mode = TripReviewMode.EDIT,
        draft = ReviewDraft(
            overall = 5,
            quality = 4,
            interesting = 5,
            `fun` = 3,
            comment = "A pleasant trip.",
            step = ReviewStep.FUN
        )
    )
}

@Composable
private fun ReviewExample(
    draft: ReviewDraft,
    mode: TripReviewMode = TripReviewMode.INITIAL
) {
    RNDTransitTheme {
        TripReviewContent(
            tripTitle = "Afternoon transit trip",
            mode = mode,
            draft = draft,
            controlsEnabled = true,
            errorMessage = null,
            onRatingChange = { _, _ -> },
            onCommentChange = {},
            onImageUrlChange = {},
            onStepChange = {},
            onSave = {},
            onDiscard = {}
        )
    }
}
