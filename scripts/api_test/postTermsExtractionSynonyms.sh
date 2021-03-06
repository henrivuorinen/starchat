#!/usr/bin/env bash

QUERY=${1:-"good morning, may I ask you a question?"}
PORT=${2:-8888}
INDEX_NAME=${4:-index_getjenny_english_0}

curl -v -H "Authorization: Basic $(echo -n 'test_user:p4ssw0rd' | base64)" \
  -H "Content-Type: application/json" -X POST "http://localhost:${PORT}/${INDEX_NAME}/extraction/synonyms" -d "
{
	\"text\": \"${QUERY}\",
	\"tokenizer\": \"base\",
	\"sentencesThreshold\": 0.0,
        \"synonymsThreshold\": 0.0,
	\"distanceFunction\": \"EMDCOSINE\",
	\"commonOrSpecificSearchTerms\": \"COMMON\",
	\"commonOrSpecificSearchPrior\": \"COMMON\",
	\"commonOrSpecificSearchObserved\": \"IDXSPECIFIC\",
	\"observedDataSource\": \"KNOWLEDGEBASE\",
	\"fieldsPrior\": \"all\",
	\"fieldsObserved\": \"all\",
	\"minWordsPerSentence\": 5,
	\"pruneTermsThreshold\": 100000,
	\"misspellMaxOccurrence\": 10,
	\"activePotentialDecay\": 1,
	\"activePotential\": true,
	\"minSentenceInfoBit\": 20,
	\"minKeywordInfo\": 8,
	\"totalInfo\": true
}
"

