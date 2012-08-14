package org.dna.mqtt.moquette.messaging.spi.impl;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import org.dna.mqtt.moquette.messaging.spi.impl.SubscriptionsStore.Token;
import org.dna.mqtt.moquette.proto.messages.AbstractMessage;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author andrea
 */
public class SubscriptionsStoreTest {

    private SubscriptionsStore store;

    public SubscriptionsStoreTest() {
    }

    @Before
    public void setUp() {
        store = new SubscriptionsStore();
    }

    @Test
    public void testSplitTopic() throws ParseException {
        List tokens = store.splitTopic("finance/stock/ibm");
        assertEqualsSeq(asArray("finance", "stock", "ibm"), tokens);

        tokens = store.splitTopic("/finance/stock/ibm");
        assertEqualsSeq(asArray(Token.EMPTY, "finance", "stock", "ibm"), tokens);

        tokens = store.splitTopic("/");
        assertEqualsSeq(asArray(Token.EMPTY), tokens);
    }

    @Test(expected = ParseException.class)
    public void testSplitTopicTwinsSlashAvoided() throws ParseException {
        store.splitTopic("/finance//stock/ibm");
    }

    @Test
    public void testSplitTopicMultiValid() throws ParseException {
        List tokens = store.splitTopic("finance/stock/#");
        assertEqualsSeq(asArray("finance", "stock", Token.MULTI), tokens);

        tokens = store.splitTopic("#");
        assertEqualsSeq(asArray(Token.MULTI), tokens);
    }

    @Test(expected = ParseException.class)
    public void testSplitTopicMultiInTheMiddleNotValid() throws ParseException {
        store.splitTopic("finance/#/closingprice");
    }

    @Test(expected = ParseException.class)
    public void testSplitTopicMultiNotAferSeparatorNotValid() throws ParseException {
        store.splitTopic("finance#");
    }

    @Test
    public void testSplitTopicSingleValid() throws ParseException {
        List tokens = store.splitTopic("finance/stock/+");
        assertEqualsSeq(asArray("finance", "stock", Token.SINGLE), tokens);

        tokens = store.splitTopic("+");
        assertEqualsSeq(asArray(Token.SINGLE), tokens);

        tokens = store.splitTopic("finance/+/ibm");
        assertEqualsSeq(asArray("finance", Token.SINGLE, "ibm"), tokens);
    }

    @Test(expected = ParseException.class)
    public void testSplitTopicSingleNotAferSeparatorNotValid() throws ParseException {
        store.splitTopic("finance+");
    }

    @Test
    public void testMatchSimple() {
        Subscription slashSub = new Subscription(null, "/", AbstractMessage.QOSType.MOST_ONE);
        store.add(slashSub);
        assertTrue(store.matches("finance").isEmpty());
        
        Subscription slashFinanceSub = new Subscription(null, "/finance", AbstractMessage.QOSType.MOST_ONE);
        store.add(slashFinanceSub);
        assertTrue(store.matches("finance").isEmpty());
        
        assertTrue(store.matches("/finance").contains(slashFinanceSub));
        assertTrue(store.matches("/").contains(slashSub));
    }
    
    @Test
    public void testMatchSimpleMulti() {
        Subscription anySub = new Subscription(null, "#", AbstractMessage.QOSType.MOST_ONE);
        store.add(anySub);
        assertTrue(store.matches("finance").contains(anySub));
        
        Subscription financeAnySub = new Subscription(null, "finance/#", AbstractMessage.QOSType.MOST_ONE);
        store.add(financeAnySub);
        assertTrue(store.matches("finance").containsAll(Arrays.asList(financeAnySub, anySub)));
    }
    
    @Test
    public void testMatchingDeepMulti_one_layer() {
        Subscription anySub = new Subscription(null, "#", AbstractMessage.QOSType.MOST_ONE);
        Subscription financeAnySub = new Subscription(null, "finance/#", AbstractMessage.QOSType.MOST_ONE);
        store.add(anySub);
        store.add(financeAnySub);
        
        //Verify
        assertTrue(store.matches("finance/stock").containsAll(Arrays.asList(financeAnySub, anySub)));
        assertTrue(store.matches("finance/stock/ibm").containsAll(Arrays.asList(financeAnySub, anySub)));
    }
    
    
    @Test
    public void testMatchingDeepMulti_two_layer() {
        Subscription financeAnySub = new Subscription(null, "finance/stock/#", AbstractMessage.QOSType.MOST_ONE);
        store.add(financeAnySub);
        
        //Verify
        assertTrue(store.matches("finance/stock/ibm").contains(financeAnySub));
    }
    
    @Test
    public void testMatchSimpleSingle() {
        Subscription anySub = new Subscription(null, "+", AbstractMessage.QOSType.MOST_ONE);
        store.add(anySub);
        assertTrue(store.matches("finance").contains(anySub));
        
        Subscription financeOne = new Subscription(null, "finance/+", AbstractMessage.QOSType.MOST_ONE);
        store.add(financeOne);
        assertTrue(store.matches("finance/stock").contains(financeOne));
    }
    
    @Test
    public void testMatchManySingle() {
        Subscription manySub = new Subscription(null, "+/+", AbstractMessage.QOSType.MOST_ONE);
        store.add(manySub);
        
        //verify
        assertTrue(store.matches("/finance").contains(manySub));
    }
    
    
    @Test
    public void testMatchSlashSingle() {
        Subscription slashPlusSub = new Subscription(null, "/+", AbstractMessage.QOSType.MOST_ONE);
        store.add(slashPlusSub);
        Subscription anySub = new Subscription(null, "+", AbstractMessage.QOSType.MOST_ONE);
        store.add(anySub);
        
        //Verify
        assertEquals(1, store.matches("/finance").size());
        assertTrue(store.matches("/finance").contains(slashPlusSub));
        assertFalse(store.matches("/finance").contains(anySub));
    }
    
    
    @Test
    public void testMatchManyDeepSingle() {
        Subscription slashPlusSub = new Subscription(null, "/finance/+/ibm", AbstractMessage.QOSType.MOST_ONE);
        store.add(slashPlusSub);
        
        Subscription slashPlusDeepSub = new Subscription(null, "/+/stock/+", AbstractMessage.QOSType.MOST_ONE);
        store.add(slashPlusDeepSub);
        
        //Verify
        assertTrue(store.matches("/finance/stock/ibm").containsAll(Arrays.asList(slashPlusSub, slashPlusDeepSub)));
    }

    @Test
    public void testMatchSimpleMulti_allTheTree() {
        store.add(new Subscription(null, "#", AbstractMessage.QOSType.MOST_ONE));
        assertFalse(store.matches("finance").isEmpty());
        assertFalse(store.matches("finance/ibm").isEmpty());
    }

    @Test
    public void testMatchSimpleMulti_zeroLevel() {
        //check  MULTI in case of zero level match
        store.add(new Subscription(null, "finance/#", AbstractMessage.QOSType.MOST_ONE));
        assertFalse(store.matches("finance").isEmpty());
    }
    
    
    @Test
    public void testRemoveClientSubscriptions_existingClientID() {
        String cliendID = "FAKE_CLID_1";
        store.add(new Subscription(cliendID, "finance/#", AbstractMessage.QOSType.MOST_ONE));
        
        //Exercise
        store.removeForClient(cliendID);
        
        //Verify
        assertEquals(0, store.size());
    }
    
    @Test
    public void testRemoveClientSubscriptions_notexistingClientID() {
        String cliendID = "FAKE_CLID_1";
        store.add(new Subscription(cliendID, "finance/#", AbstractMessage.QOSType.MOST_ONE));
        
        //Exercise
        store.removeForClient("FAKE_CLID_2");
        
        //Verify
        assertEquals(1, store.size());
    }


    private static Token[] asArray(Object... l) {
        Token[] tokens = new Token[l.length];
        for (int i = 0; i < l.length; i++) {
            Object o = l[i];
            if (o instanceof Token) {
                tokens[i] = (Token) o;
            } else {
                tokens[i] = new Token(o.toString());
            }
        }

        return tokens;
    }

    private void assertEqualsSeq(Token[] exptected, List<Token> result) {
        List<Token> expectedList = Arrays.asList(exptected);
        assertEquals(expectedList, result);
    }
}