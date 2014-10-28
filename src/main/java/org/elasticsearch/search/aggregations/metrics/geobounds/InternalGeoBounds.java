/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations.metrics.geobounds;

import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.aggregations.AggregationStreams;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.metrics.InternalMetricsAggregation;

import java.io.IOException;
import java.util.Queue;

public class InternalGeoBounds extends InternalMetricsAggregation implements GeoBounds {

    public final static Type TYPE = new Type("geo_bounds");
    public final static AggregationStreams.Stream STREAM = new AggregationStreams.Stream() {
        @Override
        public InternalGeoBounds readResult(StreamInput in) throws IOException {
            InternalGeoBounds result = new InternalGeoBounds();
            result.readFrom(in);
            return result;
        }
    };
    
    private double top;
    private double bottom;
    private double posLeft;
    private double posRight;
    private double negLeft;
    private double negRight;
    private boolean wrapLongitude;

    InternalGeoBounds() {
    }
    
    InternalGeoBounds(String name, double top, double bottom, double posLeft, double posRight,
            double negLeft, double negRight, boolean wrapLongitude) {
        super(name);
        this.top = top;
        this.bottom = bottom;
        this.posLeft = posLeft;
        this.posRight = posRight;
        this.negLeft = negLeft;
        this.negRight = negRight;
        this.wrapLongitude = wrapLongitude;
    }

    @Override
    public Type type() {
        return TYPE;
    }
    
    @Override
    public InternalAggregation reduce(ReduceContext reduceContext) {
        double top = Double.NEGATIVE_INFINITY;
        double bottom = Double.POSITIVE_INFINITY;
        double posLeft = Double.POSITIVE_INFINITY;
        double posRight = Double.NEGATIVE_INFINITY;
        double negLeft = Double.POSITIVE_INFINITY;
        double negRight = Double.NEGATIVE_INFINITY;

        for (InternalAggregation aggregation : reduceContext.aggregations()) {
            InternalGeoBounds bounds = (InternalGeoBounds) aggregation;

            if (bounds.top > top) {
                top = bounds.top;
            }
            if (bounds.bottom < bottom) {
                bottom = bounds.bottom;
            }
            if (bounds.posLeft < posLeft) {
                posLeft = bounds.posLeft;
            }
            if (bounds.posRight > posRight) {
                posRight = bounds.posRight;
            }
            if (bounds.negLeft < negLeft) {
                negLeft = bounds.negLeft;
            }
            if (bounds.negRight > negRight) {
                negRight = bounds.negRight;
            }
        }
        return new InternalGeoBounds(name, top, bottom, posLeft, posRight, negLeft, negRight, wrapLongitude);
    }

    @Override
    public Object getProperty(Queue<String> path) {
        if (path.isEmpty()) {
            return this;
        } else if (path.size() == 1) {
            BoundingBox boundingBox = resolveBoundingBox();
            String bBoxSide = path.poll();
            switch (bBoxSide) {
            case "top":
                return boundingBox.topLeft.lat();
            case "left":
                return boundingBox.topLeft.lon();
            case "bottom":
                return boundingBox.bottomRight.lat();
            case "right":
                return boundingBox.bottomRight.lon();
            default:
                throw new ElasticsearchIllegalArgumentException("Found unknown path element [" + bBoxSide + "] in [" + getName() + "]");
            }
        } else if (path.size() == 2) {
            BoundingBox boundingBox = resolveBoundingBox();
            GeoPoint cornerPoint = null;
            String cornerString = path.poll();
            switch (cornerString) {
            case "top_left":
                cornerPoint = boundingBox.topLeft;
                break;
            case "bottom_right":
                cornerPoint = boundingBox.bottomRight;
                break;
            default:
                throw new ElasticsearchIllegalArgumentException("Found unknown path element [" + cornerString + "] in [" + getName() + "]");
            }
            String latLonString = path.poll();
            switch (latLonString) {
            case "lat":
                return cornerPoint.lat();
            case "lon":
                return cornerPoint.lon();
            default:
                throw new ElasticsearchIllegalArgumentException("Found unknown path element [" + latLonString + "] in [" + getName() + "]");
            }
        } else {
            throw new ElasticsearchIllegalArgumentException("path not supported for [" + getName() + "]: " + path);
        }
    }

    @Override
    public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        GeoPoint topLeft = topLeft();
        GeoPoint bottomRight = bottomRight();
        if (topLeft != null) {
            builder.startObject("bounds");
            builder.startObject("top_left");
            builder.field("lat", topLeft.lat());
            builder.field("lon", topLeft.lon());
            builder.endObject();
            builder.startObject("bottom_right");
            builder.field("lat", bottomRight.lat());
            builder.field("lon", bottomRight.lon());
            builder.endObject();
            builder.endObject();
        }
        return builder;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        name = in.readString();
        top = in.readDouble();
        bottom = in.readDouble();
        posLeft = in.readDouble();
        posRight = in.readDouble();
        negLeft = in.readDouble();
        negRight = in.readDouble();
        wrapLongitude = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeDouble(top);
        out.writeDouble(bottom);
        out.writeDouble(posLeft);
        out.writeDouble(posRight);
        out.writeDouble(negLeft);
        out.writeDouble(negRight);
        out.writeBoolean(wrapLongitude);
    }

    public static void registerStream() {
        AggregationStreams.registerStream(STREAM, TYPE.stream());
    }
    
    private static class BoundingBox {
        private final GeoPoint topLeft;
        private final GeoPoint bottomRight;
        
        public BoundingBox(GeoPoint topLeft, GeoPoint bottomRight) {
            this.topLeft = topLeft;
            this.bottomRight = bottomRight;
        }
        
        public GeoPoint topLeft() {
            return topLeft;
        }
        
        public GeoPoint bottomRight() {
            return bottomRight;
        }
    }
    
    private BoundingBox resolveBoundingBox() {
        if (Double.isInfinite(top)) {
            return null;
        } else if (Double.isInfinite(posLeft)) {
            return new BoundingBox(new GeoPoint(top, negLeft), new GeoPoint(bottom, negRight));
        } else if (Double.isInfinite(negLeft)) {
            return new BoundingBox(new GeoPoint(top, posLeft), new GeoPoint(bottom, posRight));
        } else if (wrapLongitude) {
            double unwrappedWidth = posRight - negLeft;
            double wrappedWidth = (180 - posLeft) - (-180 - negRight);
            if (unwrappedWidth <= wrappedWidth) {
                return new BoundingBox(new GeoPoint(top, negLeft), new GeoPoint(bottom, posRight));
            } else {
                return new BoundingBox(new GeoPoint(top, posLeft), new GeoPoint(bottom, negRight));
            }
        } else {
            return new BoundingBox(new GeoPoint(top, negLeft), new GeoPoint(bottom, posRight));
        }
    }

    @Override
    public GeoPoint topLeft() {
        BoundingBox boundingBox = resolveBoundingBox();
        if (boundingBox == null) {
            return null;
        } else {
            return boundingBox.topLeft();
        }
    }

    @Override
    public GeoPoint bottomRight() {
        BoundingBox boundingBox = resolveBoundingBox();
        if (boundingBox == null) {
            return null;
        } else {
            return boundingBox.bottomRight();
        }
    }

}
