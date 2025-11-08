package com.graphhopper.config;

import com.graphhopper.util.CustomModel;
import com.graphhopper.util.PMap;
import com.graphhopper.util.TurnCostsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests pour `Profile` en utilisant Mockito pour simuler plusieurs dépendances.
 */
public class ProfileMockitoTest {

    @Test
    public void testSetCustomModelCallsInternalAndStoresModel() {
        // mock deux dépendances : CustomModel et TurnCostsConfig (au moins 2 mocks pour respecter la consigne)
        CustomModel customModel = mock(CustomModel.class);
        TurnCostsConfig turnCosts = mock(TurnCostsConfig.class);

        Profile p = new Profile("car");

        // vérifie que setTurnCostsConfig fonctionne avec un mock
        p.setTurnCostsConfig(turnCosts);
        assertSame(turnCosts, p.getTurnCostsConfig());
        assertTrue(p.hasTurnCosts());

        // appelle setCustomModel et vérifie que customModel.internal() est invoqué
        p.setCustomModel(customModel);
        verify(customModel, atLeastOnce()).internal();

        // récupère le CustomModel via getCustomModel()
        CustomModel stored = p.getCustomModel();
        assertNotNull(stored);
        // comme PMap stocke l'objet, il doit être le même que celui passé
        // (ou un équivalent selon implémentation). On vérifie la présence dans les hints
        PMap hints = p.getHints();
        assertTrue(hints.toMap().containsKey(CustomModel.KEY));
    }

    @Test
    public void testPutHintRejectsReservedKeysAndChaining() {
        Profile p = new Profile("bike");

        // clé normale doit fonctionner et permettre le chaînage
        Profile r = p.putHint("foo", "bar");
        assertSame(p, r);
        assertEquals("bar", p.getHints().getString("foo", null));

        // clés réservées provoquent IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> p.putHint("u_turn_costs", 5));
        assertThrows(IllegalArgumentException.class, () -> p.putHint("vehicle", "car"));
    }
}
