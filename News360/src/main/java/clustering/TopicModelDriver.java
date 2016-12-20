package tutorial;

public class TopicModelDriver {
	public static void main(String[] args) throws Exception {
		String tweetFile = "tweets.txt";
		String outClusterPath = "clusters";
		TopicModel tp = new TopicModel(20, tweetFile, outClusterPath, "log.txt");
		tp.findHotClusters();
		tp.showClusterHotDegrees();
		tp.showClusterTweets();
//		tp.showRepresentiveWords();
//		tp.showHashtageTweetCount();
	}
}
