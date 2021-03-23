package org.jadice.tutorial;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.jadice.server.cloud.client.DirectorClient;
import com.jadice.server.cloud.client.DirectorClientBuilder;
import com.jadice.server.cloud.client.api.director.domain.Job;
import com.jadice.server.cloud.client.api.director.domain.Job.State;
import com.jadice.server.cloud.client.api.director.domain.JobCreationResult;
import com.jadice.server.cloud.client.api.director.domain.JobExecutionResult;
import com.jadice.server.cloud.worker.StreamDescriptor;
import com.jadice.server.cloud.worker.StreamReference;
import com.jadice.server.cloud.worker.WorkerInvocation;

/**
 * A simple OCR test which performs the following actions:
 * <ul>
 * <li>Create an image</li>
 * <li>Use Jadice Server OCR worker to retrieve the OCR result</li>
 * <li>Compare the result with the expected text</li>
 * </ul>
 */
public class OCRTest {
  // Parameters to access the jadice server
  private static String directorURL = "http://localhost:8080";
  private static String s3ProxyURL = "http://localhost:7082";
  private static String authToken = "THE-JADICE-SERVER-ACCESS-TOKEN";

  // Create a single director client to use in tests
  private static DirectorClient directorClient = createDirectorClient();

  private static DirectorClient createDirectorClient() {
    DirectorClient directorClient = new DirectorClientBuilder().setDirectorUri(URI.create(directorURL)).setS3ProxyUri(
        URI.create(s3ProxyURL)).createDirectorClient();
    directorClient.addHeader("Authorization", "Bearer " + authToken);
    return directorClient;
  }

  /**
   * A simple OCR test which performs the following actions:
   * <ul>
   * <li>Create an image containing text</li>
   * <li>Use Jadice Server OCR worker to retrieve the OCR result</li>
   * <li>Compare the result with the expected text</li>
   * </ul>
   * 
   * @throws Exception
   */
  @Test
  public void test_correctOCRResult() throws Exception {
    // The text to recognize
    String text = "OCR test";

    // Create an image (or load via ImageIO.read(...) and adjust expected text)
    BufferedImage image = createImage();

    // Get byte contents (png)
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(image, "png", baos);
    byte[] imgData = baos.toByteArray();

    // Upload the image to S3
    String fileName = "temp_ocr.png";
    String mimeType = "image/png";
    URI uploadFile = directorClient.uploadFile(imgData, mimeType, fileName);

    // Call the Jadice Server
    String ocrResultPlainText = invokeJadiceServerOCRWorker(uploadFile, mimeType, fileName);

    // Assert the result
    assertEquals(text, ocrResultPlainText);
  }

  /**
   * Creates the configuration parameters and send the OCR request to the jadice server.
   * <p>
   * 
   * The timeouts in this Demo implementation are hard coded to 60sec.
   * 
   * @param uploadFile the URI for the uploaded image data
   * @param contentType mime type of image
   * @param filename the file name
   * @return the plain text OCR result
   * @throws Exception
   */
  private String invokeJadiceServerOCRWorker(URI uploadFile, String contentType, String filename) throws Exception {
    // 1) Create the configuration for the OCR invocation
    String workerName = "ocr";
    WorkerInvocation workerInvocation = new WorkerInvocation();
    StreamReference sourceItem = new StreamReference();
    StreamDescriptor descriptor = new StreamDescriptor();
    descriptor.setMimeType(contentType);
    descriptor.setFileName(filename);
    sourceItem.setDescriptor(descriptor);
    sourceItem.setUri(uploadFile);
    workerInvocation.addSourceItem(sourceItem);

    final Map<String, String> ocrWorkerConfiguration = new HashMap<>();
    // See documentation for possible output formats. Most common formats are: text,hocr
    // Multiple outputs can be requested at once (comma separated). In this demo we only fetch
    // plain text.
    ocrWorkerConfiguration.put("output-formats", "text");
    workerInvocation.setConfiguration(ocrWorkerConfiguration);

    // 2) Invoke jadice server and wait for results
    JobCreationResult creationResult = directorClient.createJob(workerName, workerInvocation);
    long jobId = creationResult.getJobId();
    State jobState = directorClient.waitTillJobIsStarted(jobId, 10000);

    if (!directorClient.isFinalState(jobState)) {
      jobState = directorClient.waitForFinalState(jobId, 60000); // 1min timout for demo
    }
    if (Job.State.FINISHED.equals(jobState)) {
      JobExecutionResult jobResult = directorClient.retrieveJobResult(jobId);
      if (jobResult.isProcessingSuccess()) {
        // 3) Return the text result (first element of "output-formats" was text so this is the
        // plain text result)
        String ocrPlain = getResultText(jobResult, 0);
        return ocrPlain;
      } else {
        throw new Exception(
            "OCR Job did not complete successfully: " + jobResult.getWorkerProcessingFailed().getReason());
      }
    } else {
      throw new Exception(
          String.format("Final Job state of job %s is not FINISHED, but %s after timeout", jobId, jobState));
    }
  }

  /**
   * Utility method to get the result as string for the given output index.
   * 
   * @param jobResult the job result
   * @param outputIndex the index
   * @return the result as string
   * @throws IOException
   */
  private String getResultText(JobExecutionResult jobResult, int outputIndex) throws IOException {
    final StreamReference streamReference = jobResult.getWorkerResult().getOutput().get(outputIndex);

    InputStream resultStream = directorClient.downloadFile(streamReference.getUri());
    StringBuilder sb = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(resultStream))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (sb.length() > 0) {
          sb.append(System.lineSeparator());
        }
        sb.append(line.trim());
      }
    }
    return sb.toString().trim();
  }

  /**
   * Creates a BufferedImage containing the text "OCR test".
   *
   * @return the BufferedImage
   */
  private static BufferedImage createImage() {
    String text = "OCR test";
    int width = 400;
    int height = 150;
    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
    Graphics2D g = (Graphics2D) img.getGraphics();

    g.setColor(Color.white);
    g.fillRect(0, 0, width, height);

    g.setColor(Color.black);
    g.setFont(new Font("dialog", Font.PLAIN, 24));
    g.drawString(text, 10, height / 2);

    return img;
  }
}