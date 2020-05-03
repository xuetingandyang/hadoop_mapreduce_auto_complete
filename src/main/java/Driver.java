import java.io.IOException;

import com.google.inject.internal.cglib.proxy.$Callback;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.db.DBConfiguration;
import org.apache.hadoop.mapreduce.lib.db.DBOutputFormat;
import org.apache.hadoop.mapreduce.lib.db.DBWritable;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;


public class Driver {

	public static void main(String[] args) throws ClassNotFoundException, IOException, InterruptedException {

		//The arguments read from command line ->
		// For MapReduce 1: inputDir, outputDir, NoGram
		// For MapReduce 2: threshold, topK

		String inputDir = args[0];
		String outputDir = args[1];
		String noGram = args[2];
		String threshold = args[3];
		String topK = args[4];

		/// job1
		Configuration configuration1 = new Configuration();
		// we need read sentence by sentence. BUT the default is read by line (\n). so change delimiter to "."
		configuration1.set("textinputformat.record.delimiter", ".");
		configuration1.set("noGram", noGram);

		Job job1 = Job.getInstance(configuration1);
		job1.setJobName("NGram");
		job1.setJarByClass(Driver.class);

		job1.setMapperClass(NGramLibraryBuilder.NGramMapper.class);
		job1.setReducerClass(NGramLibraryBuilder.NGramReducer.class);

		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(IntWritable.class);

		job1.setInputFormatClass(TextInputFormat.class);  // read from HDFS
		job1.setOutputFormatClass(TextOutputFormat.class);

		TextInputFormat.setInputPaths(job1, new Path(inputDir));
		TextOutputFormat.setOutputPath(job1, new Path(outputDir));

		// next MapReduce job need to wait for the completion of MapReduce JOB1, suggest do that for each MAPREDUCE job
		job1.waitForCompletion(true);

//		//how to connect two jobs?
//		// last output is second input

		/// job2
		Configuration configuration2 = new Configuration();
		configuration2.set("threshold", threshold);
		configuration2.set("topK", topK);

		// DB configuration setting
		DBConfiguration.configureDB(
			configuration2,
			"com.mysql.jdbc.Driver",
			"jjdbc:mysql://your_pc_ip_address:mysql_port/database_name",
			"root",
			"your_mysql_password"
		);

		Job job2 = Job.getInstance(configuration2);
		job2.setJobName("LanguageModel");
		job2.setJarByClass(Driver.class);

		job2.setMapperClass(LanguageModel.Map.class);
		job2.setReducerClass(LanguageModel.Reduce.class);

		// Change "path_to_ur_connector" to customized path for the mysql-connector-java*.jar
		// this is the path in HDFS /mysql/java-mysql-connector
		job2.addArchiveToClassPath(new Path("/mysql/mysql-connector-java-5.1.39-bin.jar"));

		// set map output key and map output value: because the outputKey and outputValue of mapper and reducer are different
		job2.setMapOutputKeyClass(Text.class);			// set Mapper's outputKey and outputValue
		job2.setMapOutputValueClass(Text.class);
		job2.setOutputKeyClass(DBOutputWritable.class);    	// default for Reducer's outputKey and outputValue
		job2.setOutputValueClass(NullWritable.class);

		job2.setInputFormatClass(TextInputFormat.class);
		job2.setOutputFormatClass(DBOutputFormat.class);

		DBOutputFormat.setOutput(job2, "output",
				new String[] {"starting_phrase", "following_word", "count"});

		TextInputFormat.setInputPaths(job2, outputDir);
		job2.waitForCompletion(true);
		
	}

}
