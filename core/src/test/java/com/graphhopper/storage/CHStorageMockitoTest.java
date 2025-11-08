package com.graphhopper.storage;

import com.graphhopper.routing.ch.PrepareEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests Mockito simples pour CHStorage.
 * On simule `Directory` pour retourner des `RAMIntDataAccess` (spies) et vérifier le comportement.
 */
public class CHStorageMockitoTest {

    @Test
    public void testAjoutRaccourciNodeEtLecture() {
        Directory dir = mock(Directory.class);

        // Retourne DAType.RAM_INT pour les appels de type par défaut
        when(dir.getDefaultType(anyString(), anyBoolean())).thenReturn(DAType.RAM_INT);

        // Utilise deux DataAccess mockés et simule setInt/getInt via des maps locales
        DataAccess nodesDA = mock(DataAccess.class);
        DataAccess shortcutsDA = mock(DataAccess.class);

        // maps pour simuler l'espace mémoire des DataAccess (bytePos -> int)
        java.util.Map<Long, Integer> nodesMem = new java.util.HashMap<>();
        java.util.Map<Long, Integer> shortcutsMem = new java.util.HashMap<>();

        // lorsque Directory.create est appelé, renvoyer le mock approprié selon le nom
        when(dir.create(startsWith("nodes_ch_"), any(DAType.class), anyInt())).thenReturn(nodesDA);
        when(dir.create(startsWith("shortcuts_"), any(DAType.class), anyInt())).thenReturn(shortcutsDA);

        // stub pour nodesDA setInt/getInt
        doAnswer(inv -> {
            long pos = inv.getArgument(0);
            int val = inv.getArgument(1);
            nodesMem.put(pos, val);
            return null;
        }).when(nodesDA).setInt(anyLong(), anyInt());
        when(nodesDA.getInt(anyLong())).thenAnswer(inv -> {
            long pos = inv.getArgument(0);
            return nodesMem.getOrDefault(pos, 0);
        });

        // stub pour shortcutsDA setInt/getInt
        doAnswer(inv -> {
            long pos = inv.getArgument(0);
            int val = inv.getArgument(1);
            shortcutsMem.put(pos, val);
            return null;
        }).when(shortcutsDA).setInt(anyLong(), anyInt());
        when(shortcutsDA.getInt(anyLong())).thenAnswer(inv -> {
            long pos = inv.getArgument(0);
            return shortcutsMem.getOrDefault(pos, 0);
        });

        // stub minimal pour headers/flush (no-op)
        doNothing().when(nodesDA).setHeader(anyInt(), anyInt());
        doNothing().when(shortcutsDA).setHeader(anyInt(), anyInt());
        doNothing().when(nodesDA).flush();
        doNothing().when(shortcutsDA).flush();

        CHStorage storage = new CHStorage(dir, "test", 128, false);
        storage.create(5, 5);

        // ajoute un raccourci basé sur des nœuds
        int sc = storage.shortcutNodeBased(2, 3, PrepareEncoder.getScFwdDir(), 12.345, 7, 9);
        assertEquals(0, sc);
        assertEquals(1, storage.getShortcuts());

        long ptr = storage.toShortcutPointer(0);
        assertEquals(2, storage.getNodeA(ptr));
        assertEquals(3, storage.getNodeB(ptr));
        assertEquals(12.345, storage.getWeight(ptr), 1e-6);
        assertEquals(7, storage.getSkippedEdge1(ptr));
        assertEquals(9, storage.getSkippedEdge2(ptr));

        // vérifie que Directory.create a été appelé pour nodes_ch_ et shortcuts_
        verify(dir, atLeastOnce()).create(startsWith("nodes_ch_"), any(DAType.class), anyInt());
        verify(dir, atLeastOnce()).create(startsWith("shortcuts_"), any(DAType.class), anyInt());
    }

    @Test
    public void testFlushEcritEnTetesEtNeLancePasDErreur() {
        Directory dir = mock(Directory.class);
        when(dir.getDefaultType(anyString(), anyBoolean())).thenReturn(DAType.RAM_INT);
    // Use mocked DataAccess objects and simulate their memory for header/flush verification
    DataAccess nodesDA = mock(DataAccess.class);
    DataAccess shortcutsDA = mock(DataAccess.class);
    java.util.Map<Long, Integer> nodesMem = new java.util.HashMap<>();
    java.util.Map<Long, Integer> shortcutsMem = new java.util.HashMap<>();

    when(dir.create(startsWith("nodes_ch_"), any(DAType.class), anyInt())).thenReturn(nodesDA);
    when(dir.create(startsWith("shortcuts_"), any(DAType.class), anyInt())).thenReturn(shortcutsDA);

    doAnswer(inv -> { nodesMem.put(inv.getArgument(0), inv.getArgument(1)); return null; })
        .when(nodesDA).setInt(anyLong(), anyInt());
    when(nodesDA.getInt(anyLong())).thenAnswer(inv -> nodesMem.getOrDefault(inv.getArgument(0), 0));
    doAnswer(inv -> { shortcutsMem.put(inv.getArgument(0), inv.getArgument(1)); return null; })
        .when(shortcutsDA).setInt(anyLong(), anyInt());
    when(shortcutsDA.getInt(anyLong())).thenAnswer(inv -> shortcutsMem.getOrDefault(inv.getArgument(0), 0));

    doNothing().when(nodesDA).setHeader(anyInt(), anyInt());
    doNothing().when(shortcutsDA).setHeader(anyInt(), anyInt());
    doNothing().when(nodesDA).flush();
    doNothing().when(shortcutsDA).flush();

    CHStorage storage = new CHStorage(dir, "flushTest", 256, false);
        storage.create(3, 2);
        // ajoute un raccourci pour que les headers reflètent >0 raccourcis
        storage.shortcutNodeBased(0, 1, PrepareEncoder.getScFwdDir(), 5.0, 0, 1);

        // appelle flush() et vérifie que cela ne lance pas d'exception
        storage.flush();

        // vérifie que Directory.create(...) a été appelé (nodes_ch_ et shortcuts_)
        verify(dir, atLeastOnce()).create(startsWith("nodes_ch_"), any(DAType.class), anyInt());
        verify(dir, atLeastOnce()).create(startsWith("shortcuts_"), any(DAType.class), anyInt());

        // vérifications simples sur les compteurs
        assertEquals(3, storage.getNodes());
        assertEquals(1, storage.getShortcuts());
    }
}
