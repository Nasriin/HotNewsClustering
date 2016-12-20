package tutorial;

import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.pipe.iterator.*;
import cc.mallet.topics.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.io.*;

import twitter4j.HashtagEntity;
import twitter4j.Status;

public class TopicModel {
	private static final int NUMBER_OF_ITERATIONS = 2000;
	private static final int NUMBER_OF_THEREADS = 8;
	private static final double ALPHA_BETA = 0.01;
	private static final int NUMBER_OF_REPRESENTIVE_WORDS = 10;
	private static final int NUMBER_OF_REPRESENTIVE_CLUSTERS = 5;
	private String tweetsFile;
	private String outClusterPath;
	private int number_of_topics;
	private InstanceList instances;
	private ParallelTopicModel model;
	private Map<Integer,HashMap<String,Double>> clusterTweets;
	private Map<Integer,Double> clusterHotdegree;
	private Map<String, List<Long>> hashtagTweetid;
	private Map<Integer, Double> clusterDistributionMap;
	private double[] topicDistribution;
	private PrintStream out;


	TopicModel(int numTopics, String inputFile, String outClustersPath, String logPath) throws IOException {
		this.tweetsFile = inputFile;
		this.outClusterPath = outClustersPath;
		out = new PrintStream(new File(logPath));
		this.number_of_topics = numTopics;
		this.clusterTweets = new HashMap<Integer, HashMap<String,Double>>();
		this.clusterHotdegree = new HashMap<Integer, Double>();
		this.hashtagTweetid = new HashMap<String, List<Long>>();
		this.clusterDistributionMap = new HashMap<Integer, Double>();
		initalize();
	}


	private void initalize() throws IOException {
		// Make the instances
		instances = makeInstances(tweetsFile);

		// Create a model
		model = createModel(instances, number_of_topics);
	}

	public void findHotClusters() throws ClassNotFoundException, IOException, Exception {
		BufferedReader tweetsReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(tweetsFile))));

		int instance_idx = 0;
		int tweetCnt = 0;
		String tweetLine;
		double[] sumTopicDistribution = new double[number_of_topics];
		while((tweetLine = tweetsReader.readLine()) != null){
			try {
				topicDistribution = model.getTopicProbabilities(instance_idx);
			} catch (Exception e){
				throw new Exception("The number of tweets are not the same as number of instances!!");
			}
			try {
			for(int i = 0; i<topicDistribution.length; i++ ) {
				sumTopicDistribution[i] += topicDistribution[i];
			}
			} catch (Exception e) {
				throw new Exception("size of topic distribution is not equal to number of topics: "+ topicDistribution.length);
			}
			tweetCnt++;
			int highProbTopic_idx = findTopicWithHighestProbabality(topicDistribution);
			if(highProbTopic_idx == -1) {
				System.err.println("Topic distribution is empty for instance " + instance_idx);
			}
			//find tweet related to the current instance 
			Status tweet = readTweet(tweetLine);
			
			updateHashtagesTweetsMap(tweet);
			
			updateTopicTweetMap(highProbTopic_idx, tweet, topicDistribution[highProbTopic_idx]);

			updateTopicDegreePairs(highProbTopic_idx, tweet);

			//Write the tweet file into the proper cluster folder 
			saveClustersIntoFile(highProbTopic_idx, tweet);

			instance_idx++;
		}
		for(int i = 0; i< sumTopicDistribution.length; i++){
			sumTopicDistribution[i] /= tweetCnt;
			out.printf("cluster %d distibution %f\n", i, sumTopicDistribution[i]);
		}
		tweetsReader.close();
	}

	private void updateHashtagesTweetsMap(Status tweet) {
		HashtagEntity[] hashtagEntities = tweet.getHashtagEntities();
		List<Long> tweetIds;
		if(hashtagEntities.length == 0) {
			tweetIds = hashtagTweetid.get("NONE");
			if(tweetIds == null) {
				tweetIds = new ArrayList<Long>();
			}
			tweetIds.add(tweet.getId());
			hashtagTweetid.put("NONE", tweetIds);
		}
		for(int i = 0; i< hashtagEntities.length; i++){
			tweetIds = hashtagTweetid.get(hashtagEntities[i]);
			if(tweetIds == null){
				tweetIds = new ArrayList<Long>();
			}
			tweetIds.add(tweet.getId());
			hashtagTweetid.put(hashtagEntities[i].getText(), tweetIds);
		}
		
	}


	private void saveClustersIntoFile(int highProbTopic_idx, Status tweet) throws FileNotFoundException {
		File parentFolder = new File(outClusterPath, highProbTopic_idx+"");
		if(!parentFolder.exists()){
			parentFolder.mkdir();
		}
		PrintWriter pw = new PrintWriter(new FileOutputStream(new File(parentFolder.getPath(), tweet.getId()+"")));
		pw.println(tweet.getText());
		pw.close();	
	}

	private void updateTopicDegreePairs(int highProbTopic_idx, Status tweet) {
		Double chd = clusterHotdegree.get(highProbTopic_idx);
		if (chd == null)
			chd = 0d;
		int hotDegree = tweet.getFavoriteCount() + tweet.getRetweetCount();
		clusterHotdegree.put(highProbTopic_idx, chd+hotDegree);		
	}

	private void updateTopicTweetMap(int highProbTopic_idx, Status tweet, double prob) {		
		HashMap<String, Double> tweetDegreePair = clusterTweets.get(highProbTopic_idx);
		if(tweetDegreePair == null ){
			tweetDegreePair = new HashMap<String, Double>();
		}
		// hotDegree is calculated as sum of retweeted counts and favorite counts
		double hotDegree = tweet.getRetweetCount() + tweet.getFavoriteCount();
		tweetDegreePair.put(tweet.getText(), hotDegree * prob );
		clusterTweets.put(highProbTopic_idx, tweetDegreePair);		
	}

	private Status readTweet(String tweetLine) throws IOException, ClassNotFoundException {
		String[] tweetInfo = tweetLine.split(", X, ");
		String[] tweetId = tweetInfo[0].split("_");
		File tweetsFolder = null;
		try {
			tweetsFolder = new File("tweets/"+tweetId[0], tweetId[1]+".ser");
		} catch (ArrayIndexOutOfBoundsException e) {
			out.println("This line is not in the right format:\t"+ tweetLine);
		}
		FileInputStream fileIn = new FileInputStream(tweetsFolder);
		ObjectInputStream in = new ObjectInputStream(fileIn);
		Status tweet = (Status) in.readObject();
		in.close();
		return tweet;
	}

	public void showClusterHotDegrees() {
//		clusterHotdegree = sortByValue(clusterHotdegree);
		for(int key : clusterHotdegree.keySet())
			out.println("cluster ("+key+")\t Hotness_degree:\t"+ clusterHotdegree.get(key));
	}

	public void showClusterTweets() {
		for(int cluster: clusterTweets.keySet()){
//			out.print(cluster);
			Map<String, Double> sortedClusterTweets = sortByValue(clusterTweets.get(cluster));
//			out.println("\t #tweets:"+sortedClusterTweets.size());
			Iterator<String> iterator = sortedClusterTweets.keySet().iterator();
			int rank = 0;
			while(iterator.hasNext() && rank < NUMBER_OF_REPRESENTIVE_CLUSTERS){
				String tweet = iterator.next();
				out.printf("cluster(%d)\t%.2f\t%s\n",cluster, sortedClusterTweets.get(tweet), tweet);
				rank++;
			}
			out.println("--------------------------------------------------------");
		}
	}

	public void showRepresentiveWords() {
		// The data alphabet maps word IDs to strings
		Alphabet dataAlphabet = instances.getDataAlphabet();
		// Get an array of sorted sets of word ID/count pairs
		ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
		Formatter outClusters = new Formatter(new StringBuilder(), Locale.US);
		for (int topic = 0; topic < number_of_topics; topic++) {
			Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
			outClusters.format("%d\t", topic);
			int rank = 0;
			while (iterator.hasNext() && rank < NUMBER_OF_REPRESENTIVE_WORDS) {
				IDSorter idCountPair = iterator.next();
				outClusters.format("%s (%.0f) ", dataAlphabet.lookupObject(idCountPair.getID()), idCountPair.getWeight());
				rank++;
			}
			outClusters.format("\n");
		}
		out.println(outClusters);
	}
	
	public void showHashtageTweetCount() {
		Formatter outHashtags = new Formatter(new StringBuilder(), Locale.US);
		for(String hashtage : hashtagTweetid.keySet()){
			outHashtags.format("%s\t\t %d \n", hashtage, hashtagTweetid.get(hashtage).size()); 
		}
		out.println(outHashtags);
	}
	public static <K, V extends Comparable<? super V>> Map<K, V> 
	sortByValue2( Map<K, V> map )
	{
		List<Map.Entry<K, V>> list =
				new LinkedList<Entry<K, V>>( map.entrySet() );
		Collections.sort( list, new Comparator<Map.Entry<K, V>>()
				{
			public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
			{
				return ( o1.getValue() ).compareTo( o2.getValue() );
			}
				} );

		Map<K, V> result = new LinkedHashMap<K, V>();
		for (Map.Entry<K, V> entry : list)
		{
			result.put( entry.getKey(), entry.getValue() );
		}
		return result;
	}
	
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
	    return map.entrySet()
	              .stream()
	              .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
	              .collect(Collectors.toMap(
	                Map.Entry::getKey, 
	                Map.Entry::getValue, 
	                (e1, e2) -> e1, 
	                LinkedHashMap::new
	              ));
	}

	private int findTopicWithHighestProbabality(
			double[] topicDistribution) {
		int max_idx = -1;
		double max = -1;
		for(int i = 0; i< topicDistribution.length; i++) {
			if(topicDistribution[i] > max){
				max = topicDistribution[i];
				max_idx = i;
			}
		}
		return max_idx;
	}

	private ParallelTopicModel createModel(InstanceList instances,
			int numTopics) throws IOException {

		// Create a model with 100 topics, alpha_t = 0.01, beta_w = 0.01
		//  Note that the first parameter is passed as the sum over topics, while
		//  the second is the parameter for a single dimension of the Dirichlet prior.
		ParallelTopicModel model = new ParallelTopicModel(numTopics, number_of_topics * ALPHA_BETA, ALPHA_BETA);
		model.addInstances(instances);

		// Use two parallel samplers, which each look at one half the corpus and combine
		//  statistics after every iteration.
		model.setNumThreads(NUMBER_OF_THEREADS);

		// Run the model for 50 iterations and stop (this is for testing only, 
		//  for real applications, use 1000 to 2000 iterations)
		model.setNumIterations(NUMBER_OF_ITERATIONS);
		model.estimate();
		return model;
	}

	private  InstanceList makeInstances(String tweetFile)
			throws UnsupportedEncodingException, FileNotFoundException {

		// Begin by importing documents from text to feature sequences
		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

		// Pipes: lowercase, tokenize, remove stopwords, map to features
		pipeList.add( new CharSequenceLowercase() );
		pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );
		pipeList.add( new TokenSequenceRemoveStopwords(new File("stopwords2.txt"), "UTF-8", false, false, false) );
		pipeList.add( new TokenSequence2FeatureSequence() );

		InstanceList instances = new InstanceList (new SerialPipes(pipeList));

		Reader fileReader = new InputStreamReader(new FileInputStream(tweetFile), "UTF-8");
		instances.addThruPipe(new CsvIterator (fileReader, Pattern.compile("^(\\S*)[\\s,]*(\\S*)[\\s,]*(.*)$"),
				3, 2, 1)); // data, label, name fields
		return instances;
	}
	
}