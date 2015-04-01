/*
 *Copyright 2013 Rodrigo Agerri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package eus.ixa.ixa.pipe.tok;

import ixa.kaflib.KAFDocument;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.jdom2.JDOMException;

import eus.ixa.ixa.pipe.seg.RuleBasedSegmenter;
import eus.ixa.ixa.pipe.seg.SentenceSegmenter;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * ixa-pipe tokenization
 * 
 * 
 * @author ragerri
 * @version 1.0
 * 
 */

public class CLI {
  
  /**
   * Get dynamically the version of ixa-pipe-pos by looking at the MANIFEST
   * file.
   */
  private static final String version = CLI.class.getPackage()
      .getImplementationVersion();
  /**
   * Get the git commit of the ixa-pipe-pos compiled by looking at the MANIFEST
   * file.
   */
  private static final String commit = CLI.class.getPackage().getSpecificationVersion();

  /**
   * BufferedReader (from standard input) and BufferedWriter are opened. The
   * module takes plain text from standard input and produces tokenized text by
   * sentences. The tokens are then placed into the <wf> elements of KAF
   * document. The KAF document is passed via standard output.
   * 
   * @param args
   * @throws IOException
   * @throws JDOMException 
   */

  public static void main(String[] args) throws IOException, JDOMException {

    //TODO check offsets when we normalize 
    
    Namespace parsedArguments = null;

    // create Argument Parser
    ArgumentParser parser = ArgumentParsers
        .newArgumentParser("ixa-pipe-tok-" + version + ".jar")
        .description(
            "ixa-pipe-tok-" + version + " is a multilingual Tokenizer module developed by IXA NLP Group.\n");

    // specify language
    parser
        .addArgument("-l", "--lang")
        .choices("de", "en", "es", "gl", "it")
        .required(true)
        .help(
            "It is REQUIRED to choose a language to perform annotation with ixa-pipe-tok");
    
    // input tokenized and segmented text 
    parser.addArgument("--notok").action(Arguments.storeTrue())
        .help("Build KAF with already tokenized and segmented text");
    
    // specify whether input if a KAF/NAF file
    parser
        .addArgument("-k", "--kaf")
        .type(Boolean.class)
        .setDefault(false)
        .help(
            "Use this option if input is a KAF/NAF document with <raw> layer.");

    // specify KAF version
    parser.addArgument("--kafversion").setDefault("v1.opener")
        .help("Set kaf document version.");

    /*
     * Parse the command line arguments
     */

    // catch errors and print help
    try {
      parsedArguments = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.out
          .println("Run java -jar ixa-pipe-tok/target/ixa-pipe-tok-" + version + ".jar -help for details");
      System.exit(1);
    }

    /*
     * Load language and tokenizer method parameters and construct annotators,
     * read and write kaf
     */

    String lang = parsedArguments.getString("lang");
    String kafVersion = parsedArguments.getString("kafversion");
    Boolean inputKafRaw = parsedArguments.getBoolean("kaf");

    Resources resourceRetriever = new Resources();
    Annotate annotator = new Annotate();
    BufferedReader breader = null;
    BufferedWriter bwriter = null;
    KAFDocument kaf;

    // choosing tokenizer and resources by language

    InputStream nonBreaker = resourceRetriever.getNonBreakingPrefixes(lang);
    SentenceSegmenter segmenter = new RuleBasedSegmenter(nonBreaker);
    nonBreaker = resourceRetriever.getNonBreakingPrefixes(lang);
    Tokenizer tokenizer = new RuleBasedTokenizer(nonBreaker, lang);

    // reading standard input, segment and tokenize
    try {
      breader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
      bwriter = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));
      String text;

      //TODO if this option is used, get language from lang attribute in KAF 
      if (inputKafRaw) {
        // read KAF from standard input
        kaf = KAFDocument.createFromStream(breader);
        text = kaf.getRawText();
      } else {
        kaf = new KAFDocument(lang, kafVersion);
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = breader.readLine()) != null) {
          sb.append(line).append("<JA>");
        }
        text = sb.toString();
      }
      KAFDocument.LinguisticProcessor newLp = kaf.addLinguisticProcessor("text", "ixa-pipe-tok-" + lang, version + "-" + commit);
      newLp.setBeginTimestamp();
      // tokenize and create KAF
      if (parsedArguments.getBoolean("notok")) {
        annotator.tokenizedTextToKAF(text, lang, tokenizer, kaf);

      } else {
        annotator.annotateTokensToKAF(text, lang, segmenter, tokenizer, kaf);
      }
      newLp.setEndTimestamp();
      if (inputKafRaw) {
        // empty raw layer ?
        // kaf.setRawText("");
      }
      bwriter.write(kaf.toString());
      bwriter.close();

    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}