/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.jackson.Jackson;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.CustomModel;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GraphHopperProfileTest {

    private static final String GH_LOCATION = "target/gh-profile-config-gh";

    // Profile.putHints 
    /**
     * Intention :
     *   Vérifier que putHint() enregistre bien la paire (clé, valeur) dans la map des hints.
     * Données :
     *   - Profile p = new Profile("p1")
     *   - Appel : p.putHint("foo", "bar")  
     * Oracle :
     *   - Aucune exception n'est levée (test du comportement normal)
     */
    @Test
    public void profile_putHint_storeKeyValue() {
        Profile p = new Profile("p1");
        // Test que putHint() fonctionne sans lever d'exception
        assertDoesNotThrow(() -> p.putHint("foo", "bar"));

        // Test le chaînage - putHint() doit retourner le même Profile
        Profile result = p.putHint("another", "value");
        assertSame(p, result, "putHint() should return the same Profile instance for method chaining");
    }

    /**
     * Intention :
     *   S'assurer que la clé réservée "u_turn_costs" est refusée et qu'une IllegalArgumentException est levée.
     * Données :
     *   - Profile p = new Profile("p1")
     *   - Appel : p.putHint("u_turn_costs", 5)
     * Oracle :
     *   - L'appel lève IllegalArgumentException
     */
    @Test
    public void profile_putHint_rejects() {
        Profile p = new Profile("p1");

        assertThrows(IllegalArgumentException.class, () -> p.putHint("u_turn_costs", "car"));
    }

    //Intention :
    //  S'assurer que la clé réservée "vehicle" est refusée et qu'une IllegalArgumentException est levée.
    //Données :
    //  - Profile p = new Profile("p1")
    //  - Appel : p.putHint("vehicle", "car")
    //Oracle :
    //  - L'appel lève IllegalArgumentException
     
    @Test
    public void profile_putHint_rejectsVehicle() {
        Profile p = new Profile("p1");

        assertThrows(IllegalArgumentException.class, () -> p.putHint("vehicle", "car"));
    }

    /**
    * Intention : vérifier que validateProfileName rejette les noms invalides
    * Données   : noms valides (minuscules, chiffres, tirets) et invalides (majuscules, espaces, caractères spéciaux)
    * Oracle    : IllegalArgumentException pour noms non conformes, aucune exception pour noms valides
    */
    @Test
    public void profile_validateProfileName_enforcesFormat() {
        // Test noms valides - aucune exception
        assertDoesNotThrow(() -> Profile.validateProfileName("valid_name"));
        assertDoesNotThrow(() -> Profile.validateProfileName("test123"));
        assertDoesNotThrow(() -> Profile.validateProfileName("my-profile"));
        assertDoesNotThrow(() -> Profile.validateProfileName("a"));
    
        // Test noms invalides - IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> Profile.validateProfileName("Invalid_Name")); // majuscule
        assertThrows(IllegalArgumentException.class, () -> Profile.validateProfileName("invalid name")); // espace
        assertThrows(IllegalArgumentException.class, () -> Profile.validateProfileName("invalid@name")); // caractère spécial
        assertThrows(IllegalArgumentException.class, () -> Profile.validateProfileName("invalid.name")); // point
        assertThrows(IllegalArgumentException.class, () -> Profile.validateProfileName("test/name")); // slash
        assertThrows(IllegalArgumentException.class, () -> Profile.validateProfileName("")); // nom vide
    }

        /**
     * Intention :
     *   Vérifier le contrat equals/hashCode pour la classe Profile (même name -> equal et même hashCode).
     * Données :
     *   - Profile a1 = new Profile("car")
     *   - Profile a2 = new Profile("car")
     *   - Profile b = new Profile("bike")
     * Oracle :
     *   - a1.equals(a2) == true et a1.hashCode() == a2.hashCode()
     *   - a1.equals(b) == false
     *   - a1.equals(null) == false
     */
    @Test
    public void profile_equalsAndHashCode() {
        Profile a1 = new Profile("car");
        Profile a2 = new Profile("car");
        Profile b = new Profile("bike");

    // même nom -> égaux et même hash
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());

    // nom différent -> non égaux (les hashCode peuvent se percuter mais seront probablement différents)
        assertNotEquals(a1, b);

    // equals avec null
        assertNotEquals(a1, null);
    }


    @Test
    public void deserialize() throws IOException {
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        String json = "{\"name\":\"my_car\",\"weighting\":\"custom\",\"turn_costs\":{\"vehicle_types\":[\"motorcar\"]},\"foo\":\"bar\",\"baz\":\"buzz\"}";
        Profile profile = objectMapper.readValue(json, Profile.class);
        assertEquals("my_car", profile.getName());
        assertEquals(List.of("motorcar"), profile.getTurnCostsConfig().getVehicleTypes());
        assertEquals("custom", profile.getWeighting());
        assertTrue(profile.hasTurnCosts());
        assertEquals(2, profile.getHints().toMap().size());
        assertEquals("bar", profile.getHints().getString("foo", ""));
        assertEquals("buzz", profile.getHints().getString("baz", ""));
    }

    @Test
    public void duplicateProfileName_error() {
        final GraphHopper hopper = createHopper();
        assertIllegalArgument(() -> hopper.setProfiles(
                new Profile("my_profile"),
                new Profile("your_profile"),
                new Profile("my_profile")
        ), "Profile names must be unique. Duplicate name: 'my_profile'");
    }

    @Test
    public void profileWithUnknownWeighting_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(new Profile("profile").setWeighting("your_weighting"));
        assertIllegalArgument(hopper::importOrLoad,
                "Could not create weighting for profile: 'profile'",
                "Weighting 'your_weighting' not supported"
        );
    }

    @Test
    public void chProfileDoesNotExist_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(TestProfiles.constantSpeed("profile1"));
        hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("other_profile"));
        assertIllegalArgument(hopper::importOrLoad, "CH profile references unknown profile 'other_profile'");
    }

    @Test
    public void duplicateCHProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(TestProfiles.constantSpeed("profile"));
        hopper.getCHPreparationHandler().setCHProfiles(
                new CHProfile("profile"),
                new CHProfile("profile")
        );
        assertIllegalArgument(hopper::importOrLoad, "Duplicate CH reference to profile 'profile'");
    }

    @Test
    public void lmProfileDoesNotExist_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(TestProfiles.constantSpeed("profile1"));
        hopper.getLMPreparationHandler().setLMProfiles(new LMProfile("other_profile"));
        assertIllegalArgument(hopper::importOrLoad, "LM profile references unknown profile 'other_profile'");
    }

    @Test
    public void duplicateLMProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(TestProfiles.constantSpeed("profile"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile"),
                new LMProfile("profile")
        );
        assertIllegalArgument(hopper::importOrLoad, "Multiple LM profiles are using the same profile 'profile'");
    }

    @Test
    public void unknownLMPreparationProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(TestProfiles.constantSpeed("profile"));
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile").setPreparationProfile("xyz")
        );
        assertIllegalArgument(hopper::importOrLoad, "LM profile references unknown preparation profile 'xyz'");
    }

    @Test
    public void lmPreparationProfileChain_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                TestProfiles.constantSpeed("profile1"),
                TestProfiles.constantSpeed("profile2"),
                TestProfiles.constantSpeed("profile3")
        );
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile1"),
                new LMProfile("profile2").setPreparationProfile("profile1"),
                new LMProfile("profile3").setPreparationProfile("profile2")
        );
        assertIllegalArgument(hopper::importOrLoad, "Cannot use 'profile2' as preparation_profile for LM profile 'profile3', because it uses another profile for preparation itself.");
    }

    @Test
    public void noLMProfileForPreparationProfile_error() {
        final GraphHopper hopper = createHopper();
        hopper.setProfiles(
                TestProfiles.constantSpeed("profile1"),
                TestProfiles.constantSpeed("profile2"),
                TestProfiles.constantSpeed("profile3")
        );
        hopper.getLMPreparationHandler().setLMProfiles(
                new LMProfile("profile1").setPreparationProfile("profile2")
        );
        assertIllegalArgument(hopper::importOrLoad, "Unknown LM preparation profile 'profile2' in LM profile 'profile1' cannot be used as preparation_profile");
    }

    private GraphHopper createHopper() {
        final GraphHopper hopper = new GraphHopper();
        hopper.setGraphHopperLocation(GH_LOCATION);
        hopper.setStoreOnFlush(false);
        return hopper;
    }

    private static void assertIllegalArgument(Runnable runnable, String... messageParts) {
        try {
            runnable.run();
            fail("There should have been an error containing:\n\t" + Arrays.asList(messageParts));
        } catch (IllegalArgumentException e) {
            for (String messagePart : messageParts) {
                assertTrue(e.getMessage().contains(messagePart), "Unexpected error message:\n\t" + e.getMessage() + "\nExpected the message to contain:\n\t" + messagePart);
            }
        }
    }


}
