package org.apache.solr.ltr.feature.impl;

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

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.solr.ltr.feature.norm.Normalizer;
import org.apache.solr.ltr.ranking.Feature;
import org.apache.solr.ltr.ranking.FeatureScorer;
import org.apache.solr.ltr.ranking.FeatureWeight;
import org.apache.solr.ltr.util.CommonLTRParams;
import org.apache.solr.ltr.util.FeatureException;
import org.apache.solr.ltr.util.NamedParams;
import org.apache.solr.request.SolrQueryRequest;

import com.google.common.collect.Sets;

public class FieldValueFeature extends Feature {
  String fieldName;
  Set<String> fields = Sets.newHashSet();

  public FieldValueFeature() {

  }

  @Override
  public void init(String name, NamedParams params, int id)
      throws FeatureException {
    super.init(name, params, id);
    if (!params.containsKey(CommonLTRParams.FEATURE_FIELD_PARAM)) {
      throw new FeatureException("missing param field");
    }
    fieldName = (String) params.get(CommonLTRParams.FEATURE_FIELD_PARAM);
    fields.add(fieldName);
  }

  @Override
  public FeatureWeight createWeight(IndexSearcher searcher, boolean needsScores, SolrQueryRequest request, Query originalQuery, Map<String,String> efi)
      throws IOException {
    return new FieldValueFeatureWeight(searcher, name, params, norm, id, request, originalQuery, efi);
  }

  @Override
  public String toString(String f) {
    return "FieldValueFeature [field:" + fieldName + "]";

  }

  public class FieldValueFeatureWeight extends FeatureWeight {

    public FieldValueFeatureWeight(IndexSearcher searcher, String name,
        NamedParams params, Normalizer norm, int id, SolrQueryRequest request, Query originalQuery, Map<String,String> efi) {
      super(FieldValueFeature.this, searcher, name, params, norm, id, request, originalQuery, efi);
    }

    @Override
    public FeatureScorer scorer(LeafReaderContext context) throws IOException {
      return new FieldValueFeatureScorer(this, context);
    }

    public class FieldValueFeatureScorer extends FeatureScorer {

      LeafReaderContext context = null;
      DocIdSetIterator itr;

      public FieldValueFeatureScorer(FeatureWeight weight,
          LeafReaderContext context) {
        super(weight);
        this.context = context;
        itr = new MatchAllIterator();
      }

      @Override
      public float score() throws IOException {

        try {
          final Document document = context.reader().document(itr.docID(),
              fields);
          final IndexableField field = document.getField(fieldName);
          if (field == null) {
            // logger.debug("no field {}", f);
            // TODO define default value
            return 0;
          }
          final Number number = field.numericValue();
          if (number != null) {
            return number.floatValue();
          } else {
            final String string = field.stringValue();
            // boolean values in the index are encoded with the
            // chars T/F
            if (string.equals("T")) {
              return 1;
            }
            if (string.equals("F")) {
              return 0;
            }
          }
        } catch (final IOException e) {
          // TODO discuss about about feature failures:
          // do we want to return a default value?
          // do we want to fail?
        }
        // TODO define default value
        return 0;
      }

      @Override
      public String toString() {
        return "FieldValueFeature [name=" + name + " fields=" + fields + "]";
      }

      @Override
      public int docID() {
        return itr.docID();
      }

      @Override
      public DocIdSetIterator iterator() {
        return itr;
      }

    }
  }

}