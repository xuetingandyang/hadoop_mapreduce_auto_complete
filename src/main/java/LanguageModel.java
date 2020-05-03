
import java.awt.print.PrinterGraphics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.db.DBWritable;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;

public class LanguageModel {
	public static class Map extends Mapper<LongWritable, Text, Text, Text> {

		int threshold;
		// get the threshold parameter from the configuration
		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration configuration = context.getConfiguration();
			threshold = configuration.getInt("threshold", 20); // if null, default 20
		}

		@Override
		public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
			//input: nGramLibrary -> I love big data\t10 (\t: HDFS Delimiter of inputKey and inputValue, can be changed in Configuration)
			//output: split between last word and penultimate word -> key='I love big', value='data=10'

			if ((value == null) || (value.toString().trim().length() == 0)) {
				return;		// return; is not stop all procedure. Just stop for Current Line.
			}

			String line = value.toString().trim();
			String[] wordsPlusCount = line.split("\t");

			if (wordsPlusCount.length < 2) {
				return;
			}
			// split 'i love big data' -> 'i love big' & 'data=count'
			String[] words = wordsPlusCount[0].split("\\s+");		// >=1 space, \n, etc
			int count = Integer.parseInt(wordsPlusCount[1]);			// convert string to int


			// filter out 'count < threshold'
			// threshold only need to be initialized once -> put in setup()
			if (count < threshold) {
				return;
			}

			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < words.length - 1; i++) {
				sb.append(words[i]);
				sb.append(" ");		// ilovebig (wrong) -> do not forget space btw words -> I love big
			}

			String outputKey = sb.toString().trim();
			String outputValue = words[words.length - 1] + "=" + count;

			context.write(new Text(outputKey), new Text(outputValue));
		}
	}

	public static class Reduce extends Reducer<Text, Text, DBOutputWritable, NullWritable> {
		/*
		LanguageModel: filter out low frequency words.
		Reducer: select TopK words
		(Mapper -> Shuffle (combine) -> Reducer (InputValue: Iterator Text))
		 */

		int topK;
		// get topK parameter from configuration
		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration configuration = context.getConfiguration();
			topK = configuration.getInt("topK", 5);
		}

		@Override
		protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
			// InputKey: Mapper OutputKey -> Text (I love big);
			// InputValue: Mapper OutputValue -> Iterator Text <data=100, boy=200, girl=300, ... (after shuffle)

			// reverse sort, a treemap
			// <100, <day, baby, cat>>, <20, <dog, data, ..>>, ...
			TreeMap<Integer, List<String>> tm = new TreeMap<Integer, List<String>>(Collections.reverseOrder());

			// build Treemap to get TopK -> can use PriorityQueue to improve the process of finding TopK
			for (Text val: values) {
				//val: 'data=10'
				String value = val.toString().trim();
				String word = value.split("=")[0].trim();
				int count = Integer.parseInt(value.split("=")[1]);

				if (tm.containsKey(count)) {
					tm.get(count).add(word);	// update value
				} else {
					ArrayList<String> listOfWords = new ArrayList<String>();
					listOfWords.add(word);
					tm.put(count, listOfWords);

				}
			}

			// select TopK in TreeMap (cause in reverse order of key(i.e. count), just select topK (key-value) pairs)
			// <100, <day, baby, cat>>, <20, <dog, data, ..>>
			Iterator<Integer> iter = tm.keySet().iterator();
			for (int j = 0; iter.hasNext() && j < topK; ) {		// notice NO j++
				int keyCount = iter.next();
				List<String> words = tm.get(keyCount);

				for (String curWord: words) {
					// use DBOutputWritable class to write into database
					context.write(new DBOutputWritable(key.toString(), curWord, keyCount), NullWritable.get());
					j++;	// after write, j++ , no need for j++ in first for loop
				}
			}
		}
	}
}
