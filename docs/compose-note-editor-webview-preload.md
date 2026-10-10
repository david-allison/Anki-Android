# WebView preload: original 3.4–4.0 s → retained Pixel release 328 ms first / 166 ms repeat

The full historical starting point was 3.4–4.0 s on an instrumented emulator;
current values are Pixel 9 Pro Activity-to-ready medians, not a controlled
historical speedup ratio. [Release comparison](../AnkiDroid/build/reports/compose-editor/release-phone/README.md).

This experiment starts preparation at the DeckPicker's **Add note action**,
not at app startup. Measure both Add-handler-to-ready and Activity-to-ready;
otherwise moving work before onCreate could falsely look like an improvement.
The initial plus tap which opens the action menu is a separate opportunity and
is not the preload trigger in this first experiment.

## Correct trigger: FAB-menu preload saves 40 ms repeat / 99 ms first

The user intended the first plus tap. The initial experiments below incorrectly
started at the later Add-note action; they did not answer that intended question.
The corrected experiment prepares immediately after the opened menu's first draw.

Six alternating Pixel release pairs: **179.28 → 138.79 ms repeat Add-to-ready**,
**330.60 → 231.38 ms first Add-to-ready**. All six pairs improve in both groups;
all 12 prerenders activate. On the earlier Activity-based timer, repeat readiness
is **156.59 → 114.99 ms**, about 15 ms above the 100 ms target.

The cost is later menu animation work: the median slowest frame changes from
**8.44 → 21.58 ms repeat**, **9.32 → 49.75 ms first**. The menu's first draw remains
about 8–11 ms. Native typing, cancellation/reopen, editable fields and stylesheet
checks pass. Actual measured menu dwell is 256–481 ms for the candidate; there
is no UIAutomator inspection between taps. This does not measure instant Add.

This is a useful prototype requiring animation refinement, not a failed preload
idea. The prototype and exact source are archived; the clean baseline is restored.
A next hypothesis is waiting until the menu animation ends before construction,
falling back when Add arrives too quickly. No saving is claimed for that untested
scheduling change. [Full results, controls, source and restoration](../AnkiDroid/build/reports/compose-editor/fab-preload/README.md).

## ACTION_DOWN follow-up: promising, conditional on press duration

The preliminary run used the emulator while the Pixel was disconnected.
With 108–128 ms actual holds, construction completed before release in every
candidate opening. UP delivery remained about 1 ms and UP-to-menu-first-draw
about 13–16 ms. The median worst first-opening menu frame improved **39 → 17 ms**;
repeat was approximately **18 → 17 ms**. Repeat editor readiness was noisy, so
no further Pixel speedup is claimed.

With immediate-release stress gestures (10–15 ms holds), construction instead
delayed UP delivery by **23 ms first / 9 ms repeat**, and the menu appeared
**39 / 32 ms after release**. Earlier preparation is not unconditionally hitch-free.
Accessibility ACTION_CLICK fallback, native typing and cancelled-touch cleanup
passed. The prototype is archived and ordinary source restored.
[Protocol, raw results and source](../AnkiDroid/build/reports/compose-editor/touch-preload/README.md).

Breaking down those same captures, the slowest ACTION_DOWN menu frames spend
only **0.17–1.52 ms** waiting to start input processing, then **13.62–16.73 ms**
between queuing rendering synchronization and frame completion. Construction
has already ended **79–100 ms before menu opening**. These residual frame
durations are not evidence of another constructor stall; framestats alone
cannot distinguish the rendering and presentation causes within that interval.
[Frame phase analysis](../AnkiDroid/build/reports/compose-editor/touch-preload/menu-frame-phases.json).

## ACTION_DOWN on the Pixel: smoother menu, short-release limitation remains

Three alternating release pairs (12 openings) reduce the median slowest menu
frame **45.77 → 13.81 ms first / 16.34 → 9.73 ms repeat**. Actual holds were
107–122 ms, and the constructor finished 39–87 ms before release. UP-to-menu draw
remained about 9–14 ms. All prerenders activated and field/style checks passed.

Repeat Add-to-ready medians were **171.69 → 150.60 ms**, but paired deltas varied
from −50.26 to +0.07 ms. First-opening deltas had both signs. This supports the
animation change more strongly than an additional loading-speed claim; keep the
historical 115 ms repeat / 206 ms first Activity-based best separately labelled.

The paired input helper uses an accessibility connection. A separate four-opening
touch-only pilot disables that connection and confirms the animation benefit,
with no consistent loading gain; native text insertion/deletion also passes.
Its foreground guards increase menu dwell, so the two batches are not pooled.

Immediate-release stress samples show a real boundary: with 16–21 ms holds,
ACTION_DOWN construction delayed UP delivery **58 ms first / 29 ms repeat**;
the menu drew **79 / 49 ms after release**. Starting earlier hides construction
under longer presses; it cannot interrupt the synchronous constructor when a
quick release arrives. These are individual samples, not medians.
Nominal 50 ms holds (actual 69–75 ms) completed construction before release;
menu draw arrived 13 / 6 ms later. Keyboard Enter and accessibility ACTION_CLICK
both exercised the menu-opening fallback, and double-tap, cancellation/reopen
and native typing checks passed. This is not a full TalkBack navigation test.
Across the paired run and follow-ups, all 29 measured openings activated their
prerender and passed field/style validation. The clean isolated test APK was
restored, the input helper removed, and prototype source remains archived.
[Pixel protocol, raw captures and prototype](../AnkiDroid/build/reports/compose-editor/touch-preload-phone/README.md).

## APIs available in this branch

The branch already uses **androidx.webkit:webkit:1.16.0**. The installed library's
source JAR was inspected, alongside Android's reference documentation and the
Chromium implementation tests. Newer online documentation contains additional
1.17 APIs; no dependency upgrade is needed for the experiment below.

| API | Work it prepares | Fit for this editor |
| --- | --- | --- |
| `WebViewCompat.startUpWebView` | Process-wide WebView provider/browser initialization | Stable outcome-receiver overload in 1.16.0; relevant before first use, not a new page or a renderer guarantee |
| `Profile.warmUpRendererProcess` | Starts a renderer for the profile if none exists | Direct candidate at Add; experimental, feature-checked, asynchronous, no completion guarantee |
| `WebViewCompat.prerenderUrlAsync` | Loads/runs a page in the same WebView for later activation | Potential page-level saving; still requires constructing/owning that WebView, bridge calls are deferred |
| `Profile.prefetchUrlAsync` | Prefetches an HTTPS response | Poor fit: our HTML is served through per-view asset interception, not a network origin |
| `ProcessGlobalConfig.setUiThreadStartupModeV2` | Splits initial UI-thread startup into shorter tasks | App-start configuration, can lengthen total wall time; not this screen-initialization experiment |
| `NavigationListener` | Native navigation, DOMContentLoaded, paint and performance-mark callbacks | Useful instrumentation; does not itself make startup faster |
| `WebViewBuilder` | Construction/configuration restrictions and profile selection | No async WebView-construction API; still requires the UI thread |

## Startup is not page preload

`startUpWebView(context, config, WebViewOutcomeReceiver)` is stable in 1.16.0.
It accepts an executor for the portions permitted off the UI thread, while some
initialization still requires that thread. The API itself needs no feature check;
the AndroidX implementation falls back for older providers. Once initialization
has completed, repeated calls report completion promptly. It does not compile
our editor JavaScript, mount fields, checkpoint a draft or finish autofocus.
[AndroidX startup API](https://developer.android.com/reference/androidx/webkit/WebViewCompat#startUpWebView(android.content.Context,androidx.webkit.WebViewStartUpConfig,androidx.webkit.WebViewOutcomeReceiver)).

Calling other WebView APIs, including ordinary feature queries, before the
callback can force synchronous initialization and lose the benefit. This app
already calls `setWebContentsDebuggingEnabled` in `AnkiDroidApp.onCreate`, before
Add. It also has a CookieManager initialization path. The user confirmed that
blocking app-level startup is intentional for now. Leave it unchanged; neither
asynchronous provider startup nor startup-result instrumentation is in this experiment.
`WebViewStartUpResult` reports total UI startup time, the longest UI task and
blocking call locations; values can be null on unsupported providers.
[Startup diagnostics](https://developer.android.com/reference/androidx/webkit/WebViewStartUpResult).

The default configuration loads the default profile. Setting UI tasks to false
only does part of initialization, and an empty profile set explicitly skips even
the default profile. Neither is a completed warm-up for our existing editor.
[Startup configuration](https://developer.android.com/reference/androidx/webkit/WebViewStartUpConfig.Builder).

## Renderer warm-up is a distinct candidate

`Profile.warmUpRendererProcess()` can start the renderer without creating a
WebView or loading a URL. It operates on the profile the eventual WebView uses;
the current editor uses the default profile. Check `MULTI_PROFILE` before using
ProfileStore and `WARM_UP_RENDERER_PROCESS` before warming. The call is on the
UI thread but starts the process asynchronously; it has no completion callback.
If a renderer is already available, there may be little or nothing to save.
It can overlap the native Activity transition without retaining an Activity,
creating a draft, navigating a URL or changing origin/storage isolation.
[Renderer API](https://developer.android.com/reference/androidx/webkit/Profile#warmUpRendererProcess()).

The first experiment requests renderer warm-up at Add and proceeds with normal
editor navigation. It leaves app-level blocking startup unchanged, as requested,
and does not collect WebViewStartUpResult. The existing editor path remains
available on unsupported providers. This isolates renderer preparation from
provider initialization and does not relocate collection operations.

## Why prerendering needs a separate experiment

The API is per-WebView: later `loadUrl` must use that WebView and match the
prerendered HTTPS URL. It is not a portable warmed page transferable into a new
editor WebView. Cancellation is best-effort; unsupported/error/cancelled paths
must still navigate normally. The native callback reports activation or error,
not a guaranteed fully ready editor.
[Prerender API](https://developer.android.com/reference/androidx/webkit/WebViewCompat#prerenderUrlAsync(android.webkit.WebView,java.lang.String,android.os.CancellationSignal,java.util.concurrent.Executor,androidx.webkit.PrerenderOperationCallback)).

Interception itself is supported: Chromium has a successful custom-response
prerender test. But JavaScript-to-Java WebMessageListener messages are queued
until activation. Our page asks native code for translations through this bridge,
so it cannot complete the current bootstrap entirely while prerendered. Waiting
for our normal fields-ready signal before activating would be a design mistake.
The upstream native-API tests also explicitly make their WebContents visible
because background prerendering is restricted. A detached precreated WebView
must therefore be probed on the actual provider, not assumed to work.
Some HTML/script loading and compilation could still happen earlier; the net
benefit needs measurement. Upstream tests are evidence of current implementation,
not a promise that every installed provider has identical behavior.
[Chromium prerender tests](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/javatests/src/org/chromium/android_webview/test/AwPrerenderTest.java).

In contrast, the embedder Profile-prefetch API bypasses the WebView client's
`shouldInterceptRequest`. This is explicitly checked upstream. Our virtual
HTTPS origin has no network server to fetch from; do not replace asset loading
with this API or assume an ordinary HTTP prefetch cache will contain its HTML.
[Prefetch interception test](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/android_webview/javatests/src/org/chromium/android_webview/test/AwPrefetchInterceptionTest.java).

## Other new APIs

Process-global async UI startup improves responsiveness by splitting work, with
potentially longer total elapsed initialization. It must be configured once,
before WebView loads. Doing it at Add would be too late in this app, and changing
application startup is outside the screen experiment.
[ProcessGlobalConfig](https://developer.android.com/reference/androidx/webkit/ProcessGlobalConfig).

NavigationListener's page identities and native DOM/paint callbacks could improve
future profiling. DOMContentLoaded/FCP are not substitutes for our persisted-draft
and autofocus readiness condition. Back/forward cache also does not preserve a
WebView we destroy when the editor closes.
[NavigationListener](https://developer.android.com/reference/androidx/webkit/NavigationListener).

WebViewBuilder configures a view; it is not an asynchronous constructor. This
branch's API supports applying configuration to a subclass before using that
view, but that does not remove construction or renderer work.
[WebViewBuilder](https://developer.android.com/reference/androidx/webkit/WebViewBuilder).

## Renderer result: no worthwhile repeat-opening gain

Six alternating pairs on the Pixel, same R8 release APK: **178.79 → 176.84 ms**
from Add action to ready, with two of six repeat pairs slower. First-opening
readiness improves **323.42 → 315.74 ms**, but first native draw is later
(**63.77 → 81.18 ms**). This does not justify retaining renderer warm-up.
The Activity-only first-opening figure (**298 → 281 ms**) overstates the
end-to-end saving. No readiness work was removed or redefined.
[Exact results, controls and artifacts](../AnkiDroid/build/reports/compose-editor/preload-api/README.md).

## Earlier final-Add prerender: works, but no net saving at that late trigger

The isolated release experiment completed six alternating pairs, **24 valid
openings**, on the Pixel. **All 12 prerender attempts activated**; every opening
passed field/editability and stylesheet validation. The screenshot also shows
the expected focused field and keyboard. All 196 packaged editor/backend assets
match the control, and release compilation/lint passed.

| Median milliseconds | Baseline | Prerender |
| --- | ---: | ---: |
| Repeat: Add → ready | 171.60 | 175.81 |
| Repeat: Add → first native draw | 39.51 | 73.56 |
| First: Add → ready | 321.77 | 326.29 |
| First: Add → first native draw | 66.63 | 134.29 |
| Repeat: Activity onCreate → ready | 153.28 | 126.69 |

Three repeat pairs improve, three regress: **no reliable readiness gain**, with
a later first native draw. Measuring only after Activity creation would falsely
suggest a 27 ms repeat improvement. WebView construction moved before it and
cost **22 ms repeat / 55 ms first**. Add-to-ready includes this work.
The sample is too small to establish that the roughly 4 ms readiness difference
is a real regression; it is enough to reject this as a demonstrated speedup.

The detached-view handoff succeeds on this provider, despite the background
restriction concern from upstream tests. It does not establish support for
arbitrary background preloading or every provider. Prerender begins a median
52 ms before loadUrl on repeat openings, but post-activation work remains.

Both preload experiments are **archived and removed**. The five pre-experiment
source files and clean isolated release APK are restored. App-level blocking
startup, draft readiness and system settings are unchanged. The retained
166 ms release result uses the earlier shared-text control; the 172 ms baseline
here uses actual DeckPicker Add with empty Basic fields. Compare paired values
within each experiment, not the two harnesses as an optimization result.
[Exact results, phase breakdown, patch and restoration](../AnkiDroid/build/reports/compose-editor/preload-api/README.md).

The corrected menu-opening experiment above demonstrates the value of that
earlier lead time. The negative result here applies only to the final-Add trigger.
