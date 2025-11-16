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
package com.graphhopper.routing;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.routing.util.DirectedEdgeFilter;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.shapes.GHPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for DirectionResolver using Mockito to mock the Graph/EdgeExplorer/EdgeIterator
 */
public class DirectionResolverMockitoTest {
    @Test
    public void resolveDirections_singleAdjacentEdge_returnsRestricted() {
        // mocks
        Graph graph = mock(Graph.class);
        EdgeExplorer explorer = mock(EdgeExplorer.class);
        EdgeIterator iter = mock(EdgeIterator.class);
        NodeAccess na = mock(NodeAccess.class);
        DirectedEdgeFilter filter = mock(DirectedEdgeFilter.class);

        // stub graph to return explorer and nodeAccess
        when(graph.createEdgeExplorer()).thenReturn(explorer);
        when(graph.getNodeAccess()).thenReturn(na);

        // when explorer.setBaseNode called with node 5 return our iterator
        when(explorer.setBaseNode(5)).thenReturn(iter);

        // iterator: one edge then end
        when(iter.next()).thenReturn(true).thenReturn(false);
        // provide a small geometry with two points (base and next)
        PointList pl = new PointList(2, false);
        pl.add(0.0, 0.0);
        pl.add(1.0, 1.0);
        when(iter.fetchWayGeometry(FetchMode.ALL)).thenReturn(pl);
        when(iter.getEdge()).thenReturn(12);
        when(iter.getAdjNode()).thenReturn(9);

        // accept both directions
        when(filter.accept(iter, true)).thenReturn(true);
        when(filter.accept(iter, false)).thenReturn(true);

        // NodeAccess coordinates for the snapped point
        when(na.getLat(5)).thenReturn(0.0);
        when(na.getLon(5)).thenReturn(0.0);

        DirectionResolver dr = new DirectionResolver(graph, filter);
        DirectionResolverResult res = dr.resolveDirections(5, new GHPoint(2.0, 2.0));

        assertTrue(res.isRestricted());
        // in this case both right and left use the same edges (restricted with equal values)
        assertEquals(12, res.getInEdgeRight());
        assertEquals(12, res.getInEdgeLeft());
        assertEquals(12, res.getOutEdgeRight());
        assertEquals(12, res.getOutEdgeLeft());
    }
}
