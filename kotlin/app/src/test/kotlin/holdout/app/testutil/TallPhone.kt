package holdout.app.testutil

/**
 * A Robolectric screen tall enough for a whole scrolling screen to be on view,
 * so a test can assert a word at the foot of Today or the stake sheet without
 * scrolling to it first. Use as `@Config(qualifiers = TALL_PHONE)`.
 */
const val TALL_PHONE = "w412dp-h2400dp"
