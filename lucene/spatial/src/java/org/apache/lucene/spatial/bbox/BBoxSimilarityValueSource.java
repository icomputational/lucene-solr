package org.apache.lucene.spatial.bbox;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.spatial4j.core.shape.Rectangle;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.util.Bits;

import java.io.IOException;
import java.util.Map;

/**
 * An implementation of the Lucene ValueSource model to support spatial relevance ranking.
 *
 * @lucene.experimental
 */
public class BBoxSimilarityValueSource extends ValueSource {

  private final BBoxStrategy strategy;
  private final BBoxSimilarity similarity;

  public BBoxSimilarityValueSource(BBoxStrategy strategy, BBoxSimilarity similarity) {
    this.strategy = strategy;
    this.similarity = similarity;
  }

  /**
   * Returns the ValueSource description.
   *
   * @return the description
   */
  @Override
  public String description() {
    return "BBoxSimilarityValueSource(" + similarity + ")";
  }


  /**
   * Returns the DocValues used by the function query.
   *
   * @param readerContext the AtomicReaderContext which holds an AtomicReader
   * @return the values
   */
  @Override
  public FunctionValues getValues(Map context, AtomicReaderContext readerContext) throws IOException {
    AtomicReader reader = readerContext.reader();
    final NumericDocValues minX = DocValues.getNumeric(reader, strategy.field_minX);
    final NumericDocValues minY = DocValues.getNumeric(reader, strategy.field_minY);
    final NumericDocValues maxX = DocValues.getNumeric(reader, strategy.field_maxX);
    final NumericDocValues maxY = DocValues.getNumeric(reader, strategy.field_maxY);

    final Bits validMinX = DocValues.getDocsWithField(reader, strategy.field_minX);
    final Bits validMaxX = DocValues.getDocsWithField(reader, strategy.field_maxX);

    return new FunctionValues() {
      //reused
      Rectangle rect = strategy.getSpatialContext().makeRectangle(0,0,0,0);

      @Override
      public float floatVal(int doc) {
        double minXVal = Double.longBitsToDouble(minX.get(doc));
        double maxXVal = Double.longBitsToDouble(maxX.get(doc));
        // make sure it has minX and area
        if ((minXVal != 0 || validMinX.get(doc)) && (maxXVal != 0 || validMaxX.get(doc))) {
          rect.reset(
              minXVal, maxXVal,
              Double.longBitsToDouble(minY.get(doc)), Double.longBitsToDouble(maxY.get(doc)));
          return (float) similarity.score(rect, null);
        } else {
          return (float) similarity.score(null, null);
        }
      }

      @Override
      public Explanation explain(int doc) {
        // make sure it has minX and area
        if (validMinX.get(doc) && validMaxX.get(doc)) {
          rect.reset(
              Double.longBitsToDouble(minX.get(doc)), Double.longBitsToDouble(maxX.get(doc)),
              Double.longBitsToDouble(minY.get(doc)), Double.longBitsToDouble(maxY.get(doc)));
          Explanation exp = new Explanation();
          similarity.score(rect, exp);
          return exp;
        }
        return new Explanation(0, "No BBox");
      }

      @Override
      public String toString(int doc) {
        return description() + "=" + floatVal(doc);
      }
    };
  }

  /**
   * Determines if this ValueSource is equal to another.
   *
   * @param o the ValueSource to compare
   * @return <code>true</code> if the two objects are based upon the same query envelope
   */
  @Override
  public boolean equals(Object o) {
    if (o.getClass() != BBoxSimilarityValueSource.class) {
      return false;
    }

    BBoxSimilarityValueSource other = (BBoxSimilarityValueSource) o;
    return similarity.equals(other.similarity);
  }

  @Override
  public int hashCode() {
    return BBoxSimilarityValueSource.class.hashCode() + similarity.hashCode();
  }
}
