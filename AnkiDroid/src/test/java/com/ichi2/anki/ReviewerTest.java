package com.ichi2.anki;

import android.content.Intent;

import com.ichi2.anki.AbstractFlashcardViewer.JavaScriptFunction;
import com.ichi2.anki.cardviewer.ViewerCommand;
import com.ichi2.anki.exception.ConfirmModSchemaException;
import com.ichi2.libanki.Card;
import com.ichi2.libanki.Consts;
import com.ichi2.libanki.Models;
import com.ichi2.libanki.Note;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.List;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static com.ichi2.anki.AbstractFlashcardViewer.EASE_2;
import static com.ichi2.anki.AbstractFlashcardViewer.EASE_4;
import static com.ichi2.anki.AbstractFlashcardViewer.RESULT_DEFAULT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeThat;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReviewerTest extends RobolectricTest {

    @Test
    public void verifyStartupNoCollection() {
        enableNullCollection();
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNull("Collection should have been null", reviewer.getCol()));
        }
    }

    @Test
    public void verifyNormalStartup() {
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> assertNotNull("Collection should be non-null", reviewer.getCol()));
        }
    }

    @Test
    public void exitCommandWorksAfterControlsAreBlocked() {
        ensureCollectionLoadIsSynchronous();
        try (ActivityScenario<Reviewer> scenario = ActivityScenario.launch(Reviewer.class)) {
            scenario.onActivity(reviewer -> {
                reviewer.blockControls(true);
                reviewer.executeCommand(ViewerCommand.COMMAND_EXIT);
            });
            assertThat(scenario.getResult().getResultCode(), is(RESULT_DEFAULT));
        }
    }

    @Test
    public void jsTime4ShouldBeBlankIfButtonUnavailable() {
        // #6623 - easy should be blank when displaying a card with 3 buttons (after displaying a review)
        Note firstNote = addNoteUsingBasicModel("Hello", "World");
        moveToReviewQueue(firstNote.cards().get(0));

        addNoteUsingBasicModel("Hello", "World2");

        Reviewer reviewer = startReviewer();
        JavaScriptFunction javaScriptFunction = reviewer.new JavaScriptFunction();


        // The answer needs to be displayed to be able to get the time.
        displayAnswer(reviewer);
        assertThat("4 buttons should be displayed", reviewer.getAnswerButtonCount(), is(4));

        String nextTime = javaScriptFunction.ankiGetNextTime4();
        assertThat(nextTime, not(isEmptyString()));

        // Display the next answer
        reviewer.answerCard(EASE_4);

        displayAnswer(reviewer);

        assertThat("The 4th button should not be visible", reviewer.getAnswerButtonCount(), is(3));

        String learnTime = javaScriptFunction.ankiGetNextTime4();
        assertThat("If the 4th button is not visible, there should be no time4 in JS", learnTime, isEmptyString());
    }


    @Test
    public void testBuryRelatedCardsNoUndo() throws ConfirmModSchemaException {
        addNoteWithThreeCards();
        JSONObject nw = getCol().getDecks().confForDid(1).getJSONObject("new");
        nw.put("delays", new JSONArray(new int[] {1, 10, 60, 720}));
        nw.put("bury", true);

        waitForAsyncTasksToComplete();

        Reviewer reviewer = startReviewer();

        waitForAsyncTasksToComplete();

        assertCounts(reviewer,3, 0, 0);

        answerCardOrdinalAsGood(reviewer, 1); // card 1 is shown

        assertCounts(reviewer,2, 2, 0);

        answerCardOrdinalAsGood(reviewer, 1); // card 1 is shown again in Anki Desktop

        assertThat("Reviewer should be finished", reviewer.isFinishing());  // now, the reviewer is closed
    }

    @Test
    public void testBuryRelatedCardsUndo() throws ConfirmModSchemaException {
        addNoteWithThreeCards();
        JSONObject nw = getCol().getDecks().confForDid(1).getJSONObject("new");
        nw.put("delays", new JSONArray(new int[] {1, 10, 60, 720}));
        nw.put("bury", true);

        waitForAsyncTasksToComplete();

        Reviewer reviewer = startReviewer();

        waitForAsyncTasksToComplete();

        assertCounts(reviewer,3, 0, 0);

        answerCardOrdinalAsGood(reviewer, 1); // card 1 is shown

        assertCounts(reviewer,2, 2, 0); // Anki Desktop, but is this correct? AnkiDroid shows 0, 2, 0

        undo(reviewer);

        assertCounts(reviewer,3, 0, 0); // the counts are back to normal
    }

    @Test
    public void testBuryRelatedCardsUndoMultiStep() throws ConfirmModSchemaException {
        addNoteWithThreeCards();
        JSONObject nw = getCol().getDecks().confForDid(1).getJSONObject("new");
        nw.put("delays", new JSONArray(new int[] {1, 10, 10, 60, 720}));
        nw.put("bury", true);

        waitForAsyncTasksToComplete();

        Reviewer reviewer = startReviewer();

        waitForAsyncTasksToComplete();

        assertCounts(reviewer,3, 0, 0);

        answerCardOrdinalAsGood(reviewer, 1); // card 1 is shown
        answerCardOrdinalAsGood(reviewer, 1); // card 1 is shown

        undo(reviewer);

        assumeThat("Unknown what assertions to make", false, is(true));
    }

    private void assertCurrentOrdIsNot(Reviewer r, int i) {
        waitForAsyncTasksToComplete();
        int ord = r.mCurrentCard.getOrd();

        assertThat("Unexpected card ord", ord + 1, not(is(i)));
    }


    private void undo(Reviewer reviewer) {
        reviewer.undo();
        waitForAsyncTasksToComplete();
    }


    private void assertCounts(Reviewer r, int newCount, int stepCount, int revCount) {
        waitForAsyncTasksToComplete();

        List<String> countList = new ArrayList<>();
        JavaScriptFunction jsApi = r.new JavaScriptFunction();
        countList.add(jsApi.ankiGetNewCardCount());
        countList.add(jsApi.ankiGetLrnCardCount());
        countList.add(jsApi.ankiGetRevCardCount());

        List<Integer> expexted = new ArrayList<>();
        expexted.add(newCount);
        expexted.add(stepCount);
        expexted.add(revCount);

        assertThat(countList.toString(), is(expexted.toString())); // We use toString as hamcrest does not print the whole array and stops at [0].
    }

    private void answerCardOrdinalAsGood(Reviewer r, int i) {
        assertCurrentOrdIs(r, i);

        r.answerCard(EASE_2);

        waitForAsyncTasksToComplete();
    }


    private void assertCurrentOrdIs(Reviewer r, int i) {
        waitForAsyncTasksToComplete();
        int ord = r.mCurrentCard.getOrd();

        assertThat("Unexpected card ord", ord + 1, is(i));
    }


    private void addNoteWithThreeCards() throws ConfirmModSchemaException {
        Models models = getCol().getModels();
        JSONObject m = models.copy(models.current());
        m.put("name", "Three");
        models.add(m);
        m = models.byName("Three");
        models.flush();
        cloneTemplate(models, m);
        cloneTemplate(models, m);

        Note newNote = getCol().newNote();
        newNote.setField(0, "Hello");
        assertThat(newNote.model().get("name"), is("Three"));

        assertThat(getCol().addNote(newNote), is(3));
    }


    private void cloneTemplate(Models models, JSONObject m) throws ConfirmModSchemaException {
        JSONArray tmpls = m.getJSONArray("tmpls");
        JSONObject defaultTemplate = tmpls.getJSONObject(0);

        JSONObject newTemplate = defaultTemplate.deepClone();
        newTemplate.put("ord", tmpls.length());
        newTemplate.put("name", "Card " + tmpls.length() + 1);

        models.addTemplate(m, newTemplate);
    }

    private void displayAnswer(Reviewer reviewer) {
        waitForAsyncTasksToComplete();
        reviewer.displayCardAnswer();
        waitForAsyncTasksToComplete();
    }


    private Reviewer startReviewer() {
        Reviewer reviewer = super.startActivityNormallyOpenCollectionWithIntent(Reviewer.class, new Intent());
        waitForAsyncTasksToComplete();
        return reviewer;
    }


    private void moveToReviewQueue(Card reviewCard) {
        reviewCard.setQueue(Consts.QUEUE_TYPE_REV);
        reviewCard.setType(Consts.CARD_TYPE_REV);
        reviewCard.setDue(0);
        reviewCard.flush();
    }
}

