package git.Nasrin.News360.Clustering;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import twitter4j.GeoLocation;
import twitter4j.HashtagEntity;
import twitter4j.Status;

public class TweetsReader {

	private int ideal_favoriteCount;
	private int hot_favoriteCount;
	private int hot_retweetedCount;
	private List<String> newsFolder;
	public TweetsReader() {
		// TODO Auto-generated constructor stub
	}
	public ArrayList<Status> read(String tweetPath) throws IOException, ClassNotFoundException {
		ArrayList<Status> tweetsArray = new ArrayList<Status>();
		newsFolder = new ArrayList<String>();
		File tweetsFolder = new File(tweetPath);
		if(!tweetsFolder.exists()){
			System.err.println("There is not folder in following path " + tweetPath);
		} else {
			for(File file: tweetsFolder.listFiles()){
				if(!file.isDirectory()){
					System.err.println("The file \""+file+"\" should not be here!");
				} else {
					for(File tweetFile : file.listFiles()){
						FileInputStream fileIn = new FileInputStream(tweetFile);
				        ObjectInputStream in = new ObjectInputStream(fileIn);
				        Status tweet = (Status) in.readObject();
				        tweetsArray.add(tweet);
				        newsFolder.add(file.getName());
				        in.close();
					}
				}
			}
		}
		return tweetsArray;
	}
	public void makeMalletInputFormat(ArrayList<Status> tweets, String tweetsFile) throws FileNotFoundException {
		PrintWriter pw = new PrintWriter(new FileOutputStream(new File(tweetsFile)));
		int i = 0;
		for(Status tweet: tweets) {
			String tweetInstance; 
			tweetInstance = newsFolder.get(i)+"_"+tweet.getId()+", X, "+tweet.getText().replaceAll("\n", "\t");
//			tweetInstance = newsFolder.get(i)+"_"+tweet.getId()+", X, "+tweet.getText().split("... https:")[0].replaceAll("\n", "\t");
//			tweetInstance = tweet.getText().split("... https:")[0];
			pw.println(tweetInstance);
			i++;
		}
		pw.close();
	}
	public void getStatistics(ArrayList<Status> tweets) throws FileNotFoundException {
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File("statistics.txt"))));
		pw.println("FavoriteCount\tHashtags\tRetweetedCount");
		for(Status tweet: tweets){
			//Rule1
//			if()
			int favCnt = tweet.getFavoriteCount();
			HashtagEntity[] hashtagEntities = tweet.getHashtagEntities();
			String hashtags = "[";
			for(HashtagEntity h : hashtagEntities) {
				hashtags += h.getText()+",";
			}
			hashtags += "]";
			int retweetedCnt = tweet.getRetweetCount();
			pw.println(favCnt+"\t"+hashtags+"\t"+retweetedCnt);
		}
		pw.close();
	}
	public ArrayList<Status> selectHotTweets(ArrayList<Status> tweets) {
		ArrayList<Status> selectedHotTweets = new ArrayList<Status>();
		int favCnt;
		int retweetedCnt;
		for(Status tweet: tweets){
			favCnt = tweet.getFavoriteCount();
			retweetedCnt = tweet.getRetweetCount();
			//Rule1
			boolean passRule1 =false;
			boolean passRule2 = false;
			if(favCnt > hot_favoriteCount)
				passRule1 = true;
			if(retweetedCnt > hot_retweetedCount)
				passRule2 = true;
			if(passRule1 && passRule2)
				selectedHotTweets.add(tweet);
		}
		return selectedHotTweets;
	}
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		String tweetPath= "tweets";
		String tweetsFile = "tweets.txt";
		TweetsReader tr = new TweetsReader();
		ArrayList<Status> tweets = tr.read(tweetPath);
		tr.makeMalletInputFormat(tweets, tweetsFile);
//		tr.getStatistics(tweets);
		System.out.println("TweetsReader.main()");

	}

}
