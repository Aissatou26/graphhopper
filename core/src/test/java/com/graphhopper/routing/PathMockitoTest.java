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

import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.Graph;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;


/**
 * Small unit test that uses Mockito to assert that Path.forEveryEdge calls the provided visitor
 * in the expected sequence with the expected prevEdgeId values.
 */
public class PathMockitoTest {
    @Test
    public void forEveryEdge_callsVisitorWithExpectedPrevEdge() {
        // Build a tiny in-memory BaseGraph (real) and a Path that references an edge
        EncodingManager em = EncodingManager.start().add(VehicleAccess.create("car")).build();
        BaseGraph g = new BaseGraph.Builder(em).create();
        // create two nodes and one edge 0 -> 1
        g.getNodeAccess().setNode(0, 0.0, 0.0);
        g.getNodeAccess().setNode(1, 1.0, 1.0);
        EdgeIteratorState edge = g.edge(0, 1).setDistance(100);

        Path p = new Path((Graph) g);
        p.setFromNode(0);
        p.setEndNode(1);
        p.addEdge(edge.getEdge());
        p.setFound(true);

        // mock the visitor
        Path.EdgeVisitor visitor = mock(Path.EdgeVisitor.class);

        // call
        p.forEveryEdge(visitor);

        // verify that visitor.next was called once with index 0 and prevEdgeId = EdgeIterator.NO_EDGE
        verify(visitor, times(1)).next(org.mockito.ArgumentMatchers.any(EdgeIteratorState.class), eq(0), eq(EdgeIterator.NO_EDGE));
        // verify finish called once
        verify(visitor, times(1)).finish();
    }
}
