/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.core.ims.protocol.msrp;

import static com.gsma.rcs.utils.StringUtils.UTF8;

import com.gsma.rcs.core.ims.protocol.sip.SipNetworkException;
import com.gsma.rcs.core.ims.protocol.sip.SipPayloadException;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.utils.CloseableUtils;
import com.gsma.rcs.utils.IdGenerator;
import com.gsma.rcs.utils.logger.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MSRP session
 * 
 * @author jexa7410
 */
public class MsrpSession {

    // Changed by Deutsche Telekom
    /**
     * Transaction info expiry timeout in milliseconds
     */
    private static final long TRANSACTION_INFO_EXPIRY_PERIOD = 30000;

    private static final byte[] NEW_LINE = MsrpConstants.NEW_LINE.getBytes(UTF8);

    // Changed by Deutsche Telekom
    /**
     * MSRP Chunk type
     */
    public enum TypeMsrpChunk {
        TextMessage, IsComposing, MessageDisplayedReport, MessageDeliveredReport, OtherMessageDeliveredReportStatus, FileSharing, HttpFileSharing, ImageTransfer, EmptyChunk, GeoLocation, StatusReport, Unknown
    }

    // Changed by Deutsche Telekom
    /**
     * MSRP transaction object that encapsulates the and map the msgId and if the origin was from
     * displayed status message
     */
    private class MsrpTransactionInfo {
        public String mTransactionId;
        public String mMsrpMsgId;
        public String mCpimMsgId;
        public TypeMsrpChunk mTypeMsrpChunk = TypeMsrpChunk.Unknown;
        private long mTimestamp = System.currentTimeMillis();

        /**
         * MSRP transaction info constructor
         * 
         * @param transactionId MSRP transaction
         * @param msrpMsgId MSRP message ID
         * @param cpimMsgId CPIM message ID
         * @param typeMsrpChunk MSRP chunk type (see {@link TypeMsrpChunk})
         */
        public MsrpTransactionInfo(String transactionId, String msrpMsgId, String cpimMsgId,
                TypeMsrpChunk typeMsrpChunk) {
            mTransactionId = transactionId;
            mMsrpMsgId = msrpMsgId;
            mCpimMsgId = cpimMsgId;
            mTypeMsrpChunk = typeMsrpChunk;
            mTimestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("[MsrpTransactionInfo - ");
            sb.append("transactionId = ").append(mTransactionId).append(", ");
            sb.append("msrpMsgId = ").append(mMsrpMsgId).append(", ");
            sb.append("cpimMsgId = ").append(mCpimMsgId).append(", ");
            sb.append("typeMsrpChunk = ").append(mTypeMsrpChunk).append(", ");
            sb.append("timestamp = ").append(mTimestamp);
            sb.append("]");
            return sb.toString();
        }
    }

    private boolean mFailureReportOption = false;

    private boolean mSuccessReportOption = false;

    private MsrpConnection connection;

    /**
     * From path
     */
    private String mFrom;

    /**
     * To path
     */
    private String mTo;

    private boolean mCancelTransfer = false;

    private RequestTransaction mRequestTransaction;

    private DataChunks mReceivedChunks = new DataChunks();

    private MsrpEventListener mMsrpEventListener;

    /**
     * Random generator
     */
    private static Random mRandom = new Random(System.currentTimeMillis());

    private ReportTransaction mReportTransaction;

    private MsrpTransaction mMsrpTransaction;

    /**
     * File transfer progress
     */
    private Vector<Long> mProgress = new Vector<Long>();

    private long mTotalSize;

    private static final Logger sLogger = Logger.getLogger(MsrpSession.class.getSimpleName());

    // Changed by Deutsche Telekom
    /**
     * Transaction info table
     */
    private ConcurrentHashMap<String, MsrpTransactionInfo> mTransactionInfoMap;

    // Changed by Deutsche Telekom
    /**
     * Mapping of messages to transactions
     */
    private ConcurrentHashMap<String, String> mMessageTransactionMap;

    // Changed by Deutsche Telekom
    /**
     * Transaction info table locking object
     */
    private Object mTransactionMsgIdMapLock = new Object();

    // Changed by Deutsche Telekom
    /**
     * Controls if is to map the msgId from transactionId if not present on received MSRP messages
     */
    private boolean mMapMsgIdFromTransationId = false;

    /**
     * IsEstablished : set after media is successfully received
     */
    private boolean mIsEstablished = false;

    private final RcsSettings mRcsSettings;

    /**
     * Constructor
     * 
     * @param rcsSettings
     */
    public MsrpSession(RcsSettings rcsSettings) {
        // Changed by Deutsche Telekom
        setMapMsgIdFromTransationId(true);
        mRcsSettings = rcsSettings;
    }

    // Changed by Deutsche Telekom
    /**
     * On destroy this instance
     */
    @Override
    protected void finalize() throws Throwable {
        setMapMsgIdFromTransationId(false);

        super.finalize();
    }

    /**
     * Generate a unique ID for transaction
     * 
     * @return ID
     */
    private static synchronized String generateTransactionId() {
        return Long.toHexString(mRandom.nextLong());
    }

    /**
     * Is failure report requested
     * 
     * @return Boolean
     */
    public boolean isFailureReportRequested() {
        return mFailureReportOption;
    }

    /**
     * Set the failure report option
     * 
     * @param failureReportOption Boolean flag
     */
    public void setFailureReportOption(boolean failureReportOption) {
        this.mFailureReportOption = failureReportOption;
    }

    /**
     * Is success report requested
     * 
     * @return Boolean
     */
    public boolean isSuccessReportRequested() {
        return mSuccessReportOption;
    }

    /**
     * Set the success report option
     * 
     * @param successReportOption Boolean flag
     */
    public void setSuccessReportOption(boolean successReportOption) {
        this.mSuccessReportOption = successReportOption;
    }

    /**
     * Set the MSRP connection
     * 
     * @param connection MSRP connection
     */
    public void setConnection(MsrpConnection connection) {
        this.connection = connection;
    }

    /**
     * Returns the MSRP connection
     * 
     * @return MSRP connection
     */
    public MsrpConnection getConnection() {
        return connection;
    }

    /**
     * Get the MSRP event listener
     * 
     * @return Listener
     */
    public MsrpEventListener getMsrpEventListener() {
        return mMsrpEventListener;
    }

    /**
     * Add a MSRP event listener
     * 
     * @param listener Listener
     */
    public void addMsrpEventListener(MsrpEventListener listener) {
        this.mMsrpEventListener = listener;
    }

    /**
     * Returns the From path
     * 
     * @return From path
     */
    public String getFrom() {
        return mFrom;
    }

    /**
     * Set the From path
     * 
     * @param from From path
     */
    public void setFrom(String from) {
        this.mFrom = from;
    }

    /**
     * Returns the To path
     * 
     * @return To path
     */
    public String getTo() {
        return mTo;
    }

    /**
     * Set the To path
     * 
     * @param to To path
     */
    public void setTo(String to) {
        this.mTo = to;
    }

    /**
     * Close the session
     */
    public void close() {
        if (sLogger.isActivated()) {
            sLogger.debug("Close session");
        }

        // Cancel transfer
        mCancelTransfer = true;

        // Close the connection
        if (connection != null) {
            connection.close();
        }

        // Unblock request transaction
        if (mRequestTransaction != null) {
            mRequestTransaction.terminate();
        }

        // Unblock report transaction
        if (mReportTransaction != null) {
            mReportTransaction.terminate();
        }

        // Unblock MSRP transaction
        if (mMsrpTransaction != null) {
            mMsrpTransaction.terminate();
        }
    }

    // Changed by Deutsche Telekom
    /**
     * Send chunks
     * 
     * @param inputStream Input stream
     * @param msgId Message ID
     * @param contentType Content type to be sent
     * @param totalSize Total size of content
     * @param typeMsrpChunk Type of MSRP chunk
     * @throws MsrpException
     */
    public void sendChunks(InputStream inputStream, String msgId, String contentType,
            final long totalSize, TypeMsrpChunk typeMsrpChunk) throws MsrpException {
        if (sLogger.isActivated()) {
            sLogger.info("Send content (" + contentType + " - MSRP chunk type: " + typeMsrpChunk
                    + ")");
        }

        if (mFrom == null) {
            throw new MsrpException("From not set");
        }

        if (mTo == null) {
            throw new MsrpException("To not set");
        }

        if (connection == null) {
            throw new MsrpException("No connection set");
        }

        this.mTotalSize = totalSize;

        // Send content over MSRP
        try {
            byte data[] = new byte[MsrpConstants.CHUNK_MAX_SIZE];
            long firstByte = 1;
            long lastByte = 0;
            mCancelTransfer = false;
            if (mSuccessReportOption) {
                mReportTransaction = new ReportTransaction();
            } else {
                mReportTransaction = null;
            }
            if (mFailureReportOption) {
                mMsrpTransaction = new MsrpTransaction();
            } else {
                mMsrpTransaction = null;
            }

            // Changed by Deutsche Telekom
            // Calculate number of needed chunks
            final int totalChunks = (int) Math.ceil(totalSize
                    / (double) MsrpConstants.CHUNK_MAX_SIZE);

            new Thread(new Runnable() {

                @Override
                public void run() {
                    if (mMsrpTransaction != null) {
                        while ((totalChunks - mMsrpTransaction.getNumberReceivedOk()) > 0
                                && !mCancelTransfer) {
                            mMsrpEventListener.msrpTransferProgress(
                                    mMsrpTransaction.getNumberReceivedOk()
                                            * MsrpConstants.CHUNK_MAX_SIZE, totalSize);
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                /* Nothing to be done here */
                            }
                        }
                    }

                }

            }).start();

            // Changed by Deutsche Telekom
            String newTransactionId = null;

            // Changed by Deutsche Telekom
            // RFC4975, section 7.1.1. Sending SEND Requests
            // When an endpoint has a message to deliver, it first generates a new
            // Message-ID. The value MUST be highly unlikely to be repeated by
            // another endpoint instance, or by the same instance in the future.
            // Message-ID value follows the definition in RFC4975, section 9
            String msrpMsgId = IdGenerator.generateMessageID();

            // Send data chunk by chunk
            for (int i = inputStream.read(data); (!mCancelTransfer) & (i > -1); i = inputStream
                    .read(data)) {
                // Update upper byte range
                lastByte += i;

                // Changed by Deutsche Telekom
                newTransactionId = generateTransactionId();
                addMsrpTransactionInfo(newTransactionId, msrpMsgId, msgId, typeMsrpChunk);

                // Send a chunk
                // Changed by Deutsche Telekom
                sendMsrpSendRequest(newTransactionId, mTo, mFrom, msrpMsgId, contentType, i, data,
                        firstByte, lastByte, totalSize);

                // Update lower byte range
                firstByte += i;

                // Progress management
                if (mFailureReportOption) {
                    // Add value in progress vector
                    mProgress.add(lastByte);
                } else {
                    // Direct notification
                    if (!mCancelTransfer) {
                        mMsrpEventListener.msrpTransferProgress(lastByte, totalSize);
                    }
                }
            }

            if (mCancelTransfer) {
                // Transfer has been aborted
                return;
            }

            // Waiting msrpTransaction
            if (mMsrpTransaction != null) {
                // Wait until all data have been reported
                mMsrpTransaction.waitAllResponses();

                // Notify event listener
                if (mMsrpTransaction.isAllResponsesReceived()) {
                    mMsrpEventListener.msrpDataTransfered(msgId);
                } else {
                    if (!mMsrpTransaction.isTerminated()) {
                        // Changed by Deutsche Telekom
                        mMsrpEventListener.msrpTransferError(msgId, "response timeout 408",
                                typeMsrpChunk);
                    }
                }
            }

            // Waiting reportTransaction
            if (mReportTransaction != null) {
                // Wait until all data have been reported
                while (!mReportTransaction.isTransactionFinished(totalSize)) {
                    mReportTransaction.waitReport();
                    if (mReportTransaction.getStatusCode() != 200) {
                        // Error
                        break;
                    }
                }

                // Notify event listener
                if (mReportTransaction.getStatusCode() == 200) {
                    mMsrpEventListener.msrpDataTransfered(msgId);
                } else {
                    // Changed by Deutsche Telekom
                    mMsrpEventListener.msrpTransferError(msgId, "error report "
                            + mReportTransaction.getStatusCode(), typeMsrpChunk);
                }
            }

            // No transaction
            if (mMsrpTransaction == null && mReportTransaction == null) {
                // Notify event listener
                mMsrpEventListener.msrpDataTransfered(msgId);
            }
        } catch (IOException e) {
            throw new MsrpException("Send chunk failed for msgId : ".concat(msgId), e);

        } finally {
            CloseableUtils.tryToClose(inputStream);
        }
    }

    /**
     * Send empty chunk
     * 
     * @throws MsrpException
     */
    public void sendEmptyChunk() throws MsrpException {
        if (sLogger.isActivated()) {
            sLogger.info("Send an empty chunk");
        }

        if (mFrom == null) {
            throw new MsrpException("From not set");
        }

        if (mTo == null) {
            throw new MsrpException("To not set");
        }

        if (connection == null) {
            throw new MsrpException("No connection set");
        }

        // Send an empty chunk
        try {
            // Changed by Deutsche Telekom
            String newTransactionId = generateTransactionId();
            String newMsgId = generateTransactionId();
            addMsrpTransactionInfo(newTransactionId, newMsgId, null, TypeMsrpChunk.EmptyChunk);
            sendEmptyMsrpSendRequest(newTransactionId, mTo, mFrom, newMsgId);
        } catch (IOException e) {
            throw new MsrpException("Failed to send empty chunk!", e);
        }
    }

    /**
     * Send MSRP SEND request
     * 
     * @param txId Transaction ID
     * @param to To header
     * @param from From header
     * @param msrpMsgId MSRP message ID
     * @param contentType Content type
     * @param dataSize Data chunk size
     * @param data Data chunk
     * @param firstByte First byte range
     * @param lastByte Last byte range
     * @param totalSize Total size
     * @throws IOException
     * @throws MsrpException
     */
    // Changed by Deutsche Telekom
    private void sendMsrpSendRequest(String txId, String to, String from, String msrpMsgId,
            String contentType, int dataSize, byte data[], long firstByte, long lastByte,
            long totalSize) throws MsrpException {
        boolean isLastChunk = (lastByte == totalSize);
        try {
            // Create request
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(4000);
            buffer.reset();
            buffer.write(MsrpConstants.MSRP_HEADER.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write(txId.getBytes(UTF8));
            buffer.write((" " + MsrpConstants.METHOD_SEND).getBytes(UTF8));
            buffer.write(NEW_LINE);

            String toHeader = MsrpConstants.HEADER_TO_PATH + ": " + to + MsrpConstants.NEW_LINE;
            buffer.write(toHeader.getBytes(UTF8));
            String fromHeader = MsrpConstants.HEADER_FROM_PATH + ": " + from
                    + MsrpConstants.NEW_LINE;
            buffer.write(fromHeader.getBytes(UTF8));
            // Changed by Deutsche Telekom
            String msgIdHeader = MsrpConstants.HEADER_MESSAGE_ID + ": " + msrpMsgId
                    + MsrpConstants.NEW_LINE;
            buffer.write(msgIdHeader.getBytes(UTF8));

            // Write byte range
            String byteRange = MsrpConstants.HEADER_BYTE_RANGE + ": " + firstByte + "-" + lastByte
                    + "/" + totalSize + MsrpConstants.NEW_LINE;
            buffer.write(byteRange.getBytes(UTF8));

            // Write optional headers
            // Changed by Deutsche Telekom
            // According with GSMA guidelines
            if (mFailureReportOption) {
                String header = MsrpConstants.HEADER_FAILURE_REPORT + ": yes"
                        + MsrpConstants.NEW_LINE;
                buffer.write(header.getBytes(UTF8));
            }
            if (mSuccessReportOption) {
                String header = MsrpConstants.HEADER_SUCCESS_REPORT + ": yes"
                        + MsrpConstants.NEW_LINE;
                buffer.write(header.getBytes(UTF8));
            }

            // Write content type
            if (contentType != null) {
                String content = MsrpConstants.HEADER_CONTENT_TYPE + ": " + contentType
                        + MsrpConstants.NEW_LINE;
                buffer.write(content.getBytes(UTF8));
            }

            // Write data
            if (data != null) {
                buffer.write(NEW_LINE);
                buffer.write(data, 0, dataSize);
                buffer.write(NEW_LINE);
            }

            // Write end of request
            buffer.write(MsrpConstants.END_MSRP_MSG.getBytes(UTF8));
            buffer.write(txId.getBytes(UTF8));
            if (isLastChunk) {
                // '$' -> last chunk
                buffer.write(MsrpConstants.FLAG_LAST_CHUNK);
            } else {
                // '+' -> more chunk
                buffer.write(MsrpConstants.FLAG_MORE_CHUNK);
            }
            buffer.write(NEW_LINE);

            // Send chunk
            if (mFailureReportOption) {
                if (mMsrpTransaction != null) {
                    mMsrpTransaction.handleRequest();
                    mRequestTransaction = null;
                } else {
                    mRequestTransaction = new RequestTransaction(mRcsSettings);
                }
                connection.sendChunk(buffer.toByteArray());
                buffer.close();
                if (mRequestTransaction != null) {
                    mRequestTransaction.waitResponse();
                    if (!mRequestTransaction.isResponseReceived()) {
                        throw new MsrpException("timeout");
                    }
                }
            } else {
                connection.sendChunk(buffer.toByteArray());
                buffer.close();
                if (mMsrpTransaction != null) {
                    mMsrpTransaction.handleRequest();
                }
            }
        } catch (IOException e) {
            throw new MsrpException("Failed to read chunk data!", e);
        }

    }

    /**
     * Send an empty MSRP SEND request
     * 
     * @param txId Transaction ID
     * @param to To header
     * @param from From header
     * @param msrpMsgId Message ID header
     * @throws MsrpException
     * @throws IOException
     */
    // Changed by Deutsche Telekom
    private void sendEmptyMsrpSendRequest(String txId, String to, String from, String msrpMsgId)
            throws MsrpException {
        try {
            // Create request
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(4000);
            buffer.reset();
            buffer.write(MsrpConstants.MSRP_HEADER.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write(txId.getBytes(UTF8));
            buffer.write((" " + MsrpConstants.METHOD_SEND).getBytes(UTF8));
            buffer.write(NEW_LINE);

            String toHeader = MsrpConstants.HEADER_TO_PATH + ": " + to + MsrpConstants.NEW_LINE;
            buffer.write(toHeader.getBytes(UTF8));
            String fromHeader = MsrpConstants.HEADER_FROM_PATH + ": " + from
                    + MsrpConstants.NEW_LINE;
            buffer.write(fromHeader.getBytes(UTF8));
            // Changed by Deutsche Telekom
            String msgIdHeader = MsrpConstants.HEADER_MESSAGE_ID + ": " + msrpMsgId
                    + MsrpConstants.NEW_LINE;
            buffer.write(msgIdHeader.getBytes(UTF8));

            // Write end of request
            buffer.write(MsrpConstants.END_MSRP_MSG.getBytes(UTF8));
            buffer.write(txId.getBytes(UTF8));
            // '$' -> last chunk
            buffer.write(MsrpConstants.FLAG_LAST_CHUNK);
            buffer.write(NEW_LINE);

            // Send chunk
            mRequestTransaction = new RequestTransaction(mRcsSettings);
            connection.sendChunkImmediately(buffer.toByteArray());
            buffer.close();
            mRequestTransaction.waitResponse();
            if (!mRequestTransaction.isResponseReceived()) {
                throw new MsrpException("timeout");
            }
        } catch (IOException e) {
            throw new MsrpException("Failed to send empty Msrp send request!", e);
        }
    }

    /**
     * Send MSRP response
     * 
     * @param code Response code
     * @param txId Transaction ID
     * @param headers MSRP headers
     * @throws IOException
     */
    private void sendMsrpResponse(String code, String txId, Hashtable<String, String> headers)
            throws MsrpException {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(4000);
            buffer.write(MsrpConstants.MSRP_HEADER.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write(txId.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write(code.getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.HEADER_TO_PATH.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write((headers.get(MsrpConstants.HEADER_FROM_PATH)).getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.HEADER_FROM_PATH.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write((headers.get(MsrpConstants.HEADER_TO_PATH)).getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.END_MSRP_MSG.getBytes(UTF8));
            buffer.write(txId.getBytes(UTF8));
            buffer.write(MsrpConstants.FLAG_LAST_CHUNK);
            buffer.write(NEW_LINE);

            connection.sendChunk(buffer.toByteArray());
            buffer.close();
        } catch (IOException e) {
            throw new MsrpException("Failed to send Msrp response!", e);
        }
    }

    /**
     * Send MSRP REPORT request
     * 
     * @param txId Transaction ID
     * @param headers MSRP headers
     * @throws MsrpException
     * @throws IOException
     */
    private void sendMsrpReportRequest(String txId, Hashtable<String, String> headers,
            long lastByte, long totalSize) throws MsrpException {
        try {
            // Create request
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(4000);
            buffer.reset();
            buffer.write(MsrpConstants.MSRP_HEADER.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write(txId.getBytes(UTF8));
            buffer.write((" " + MsrpConstants.METHOD_REPORT).getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.HEADER_TO_PATH.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write(headers.get(MsrpConstants.HEADER_FROM_PATH).getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.HEADER_FROM_PATH.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write((headers.get(MsrpConstants.HEADER_TO_PATH)).getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.HEADER_MESSAGE_ID.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
            buffer.write(MsrpConstants.CHAR_SP);
            buffer.write((headers.get(MsrpConstants.HEADER_MESSAGE_ID)).getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.HEADER_BYTE_RANGE.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
            buffer.write(MsrpConstants.CHAR_SP);
            String byteRange = "1-" + lastByte + "/" + totalSize;
            buffer.write(byteRange.getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.HEADER_STATUS.getBytes(UTF8));
            buffer.write(MsrpConstants.CHAR_DOUBLE_POINT);
            buffer.write(MsrpConstants.CHAR_SP);
            String status = "000 200 OK";
            buffer.write(status.getBytes(UTF8));
            buffer.write(NEW_LINE);

            buffer.write(MsrpConstants.END_MSRP_MSG.getBytes(UTF8));
            buffer.write(txId.getBytes(UTF8));
            buffer.write(MsrpConstants.FLAG_LAST_CHUNK);
            buffer.write(NEW_LINE);

            // Send request
            mRequestTransaction = new RequestTransaction(mRcsSettings);
            connection.sendChunk(buffer.toByteArray());
            buffer.close();
        } catch (IOException e) {
            throw new MsrpException("Failed to send Msrp report request!", e);
        }
    }

    /**
     * Receive MSRP SEND request
     * 
     * @param txId Transaction ID
     * @param headers Request headers
     * @param flag Continuation flag
     * @param data Received data
     * @param totalSize Total size of the content
     * @throws SipNetworkException
     * @throws SipPayloadException
     */
    public void receiveMsrpSend(String txId, Hashtable<String, String> headers, int flag,
            byte[] data, long totalSize) throws SipPayloadException, SipNetworkException {
        try {
            mIsEstablished = true;
            if (sLogger.isActivated()) {
                sLogger.debug(new StringBuilder("SEND request received (flag=").append(flag)
                        .append(", transaction=").append(txId).append(", totalSize=")
                        .append(totalSize).append(")").toString());
            }

            String msgId = headers.get(MsrpConstants.HEADER_MESSAGE_ID);
            boolean failureReportNeeded = true;
            String failureHeader = headers.get(MsrpConstants.HEADER_FAILURE_REPORT);
            if ((failureHeader != null) && failureHeader.equalsIgnoreCase("no")) {
                failureReportNeeded = false;
            }
            if (failureReportNeeded) {
                sendMsrpResponse(MsrpConstants.STATUS_200_OK, txId, headers);
            }
            if (data == null) {
                if (sLogger.isActivated()) {
                    sLogger.debug("Empty chunk");
                }
                return;
            }
            mReceivedChunks.addChunk(data);

            if (flag == MsrpConstants.FLAG_LAST_CHUNK) {
                if (sLogger.isActivated()) {
                    sLogger.info("Transfer terminated");
                }
                byte[] dataContent = mReceivedChunks.getReceivedData();
                mReceivedChunks.resetCache();

                String contentTypeHeader = headers.get(MsrpConstants.HEADER_CONTENT_TYPE);
                mMsrpEventListener.msrpDataReceived(msgId, dataContent, contentTypeHeader);

                boolean successReportNeeded = false;
                String reportHeader = headers.get(MsrpConstants.HEADER_SUCCESS_REPORT);
                if ((reportHeader != null) && reportHeader.equalsIgnoreCase("yes")) {
                    successReportNeeded = true;
                }

                if (successReportNeeded) {
                    try {
                        sendMsrpReportRequest(txId, headers, dataContent.length, totalSize);
                    } catch (MsrpException e) {
                        if (sLogger.isActivated()) {
                            sLogger.error("Can't send report", e);
                        }
                        // Changed by Deutsche Telekom
                        mMsrpEventListener.msrpTransferError(msgId, e.getMessage(),
                                TypeMsrpChunk.StatusReport);
                    }
                }
            } else if (flag == MsrpConstants.FLAG_ABORT_CHUNK) {
                if (sLogger.isActivated()) {
                    sLogger.info("Transfer aborted");
                }
                mMsrpEventListener.msrpTransferAborted();
            } else if (flag == MsrpConstants.FLAG_MORE_CHUNK) {
                if (sLogger.isActivated()) {
                    sLogger.debug("Transfer in progress...");
                }
                byte[] dataContent = mReceivedChunks.getReceivedData();
                boolean resetCache = mMsrpEventListener.msrpTransferProgress(
                        mReceivedChunks.getCurrentSize(), totalSize, dataContent);

                /*
                 * Data are only consumed chunk by chunk in file transfer & image share. In a chat
                 * session only the whole message is consumed after receiving the last chunk.
                 */
                if (resetCache) {
                    mReceivedChunks.resetCache();
                }
            }
        } catch (IOException e) {
            throw new SipNetworkException(new StringBuilder(
                    "Failed to SEND request for received (flag=").append(flag)
                    .append(", transaction=").append(txId).append(", totalSize=").append(totalSize)
                    .append(")").toString(), e);
        }
    }

    /**
     * Receive MSRP response
     * 
     * @param code Response code
     * @param txId Transaction ID
     * @param headers MSRP headers
     */
    public void receiveMsrpResponse(int code, String txId, Hashtable<String, String> headers) {
        // Consider media is established when we received something
        mIsEstablished = true;

        if (sLogger.isActivated()) {
            sLogger.info("Response received (code=" + code + ", transaction=" + txId + ")");
        }

        if (mFailureReportOption) {
            // Notify progress
            if (!mCancelTransfer && mProgress.size() > 0) {
                mMsrpEventListener.msrpTransferProgress(mProgress.elementAt(0), mTotalSize);
                mProgress.removeElementAt(0);
            }
        }

        // Notify request transaction
        if (mRequestTransaction != null) {
            mRequestTransaction.notifyResponse(code, headers);
        }

        // Notify MSRP transaction
        if (mMsrpTransaction != null) {
            mMsrpTransaction.handleResponse();
        }

        // Notify event listener
        if (code != 200) {
            // Changed by Deutsche Telekom
            String cpimMsgId = null;
            TypeMsrpChunk typeMsrpChunk = TypeMsrpChunk.Unknown;
            MsrpTransactionInfo msrpTransactionInfo = getMsrpTransactionInfo(txId);
            if (msrpTransactionInfo != null) {
                cpimMsgId = msrpTransactionInfo.mCpimMsgId;
                typeMsrpChunk = msrpTransactionInfo.mTypeMsrpChunk;
            }
            mMsrpEventListener
                    .msrpTransferError(cpimMsgId, "error response " + code, typeMsrpChunk);

            // Changed by Deutsche Telekom
            // If an error is received it couldn't get any better nor worse; transaction has reached
            // final state
            removeMsrpTransactionInfo(txId);
        }

        // Don't remove transaction info in general from list as this could be a preliminary answer
    }

    /**
     * Receive MSRP REPORT request
     * 
     * @param txId Transaction ID
     * @param headers MSRP headers
     * @throws IOException
     */
    public void receiveMsrpReport(String txId, Hashtable<String, String> headers)
            throws IOException {
        // Changed by Deutsche Telekom
        // Example of an MSRP REPORT request:
        // MSRP b276bb5b0adb22f6 SEND
        // To-Path: msrp://10.108.25.89:19494/n02s00i2t0+519;tcp
        // From-Path: msrp://10.102.192.68:20000/1375944013409;tcp
        // Message-ID: MID-3BCqcBUXKA
        // Byte-Range: 1-305/305
        // Content-Type: message/cpim
        //
        // From: <sip:anonymous@anonymous.invalid>
        // To: <sip:anonymous@anonymous.invalid>
        // NS: imdn <urn:ietf:params:imdn>
        // imdn.Message-ID: Msg2BCqcBUWKA
        // DateTime: 2013-08-08T06:40:56.000Z
        // imdn.Disposition-Notification: positive-delivery, display
        //
        // Content-type: text/plain; charset=utf-8
        // Content-length: 1
        //
        // F
        // -------b276bb5b0adb22f6$
        //
        // MSRP b276bb5b0adb22f6 200 OK
        // To-Path: msrp://10.102.192.68:20000/1375944013409;tcp
        // From-Path: msrp://10.108.25.89:19494/n02s00i2t0+519;tcp
        // -------b276bb5b0adb22f6$
        //
        // MSRP n02s00i2t0+1937 REPORT
        // To-Path: msrp://10.102.192.68:20000/1375944013409;tcp
        // From-Path: msrp://10.108.25.89:19494/n02s00i2t0+519;tcp
        // Status: 000 413 413
        // Message-ID: MID-3BCqcBUXKA
        // Byte-Range: 1-305/305
        // -------n02s00i2t0+1937$

        if (sLogger.isActivated()) {
            sLogger.info("REPORT request received (transaction=" + txId + ")");
        }

        // Changed by Deutsche Telekom
        String msrpMsgId = headers.get(MsrpConstants.HEADER_MESSAGE_ID);
        String cpimMsgId = null;

        String originalTransactionId = null;
        TypeMsrpChunk typeMsrpChunk = TypeMsrpChunk.Unknown;
        MsrpTransactionInfo msrpTransactionInfo = getMsrpTransactionInfoByMessageId(msrpMsgId);
        if (msrpTransactionInfo != null) {
            typeMsrpChunk = msrpTransactionInfo.mTypeMsrpChunk;
            originalTransactionId = msrpTransactionInfo.mTransactionId;
            cpimMsgId = msrpTransactionInfo.mCpimMsgId;
            if (sLogger.isActivated()) {
                sLogger.debug("REPORT request details; originalTransactionId="
                        + originalTransactionId + "; cpimMsgId=" + cpimMsgId + "; typeMsrpChunk="
                        + typeMsrpChunk);
            }
        }

        // Changed by Deutsche Telekom
        // Test if a failure report is needed
        boolean failureReportNeeded = true;
        String failureHeader = headers.get(MsrpConstants.HEADER_FAILURE_REPORT);
        if ((failureHeader != null) && failureHeader.equalsIgnoreCase("no")) {
            failureReportNeeded = false;
        }

        // Send MSRP response if requested
        if (failureReportNeeded) {
            sendMsrpResponse(MsrpConstants.STATUS_200_OK, txId, headers);
        }

        // Check status code
        int statusCode = ReportTransaction.parseStatusCode(headers);
        if (statusCode != 200) {
            // Changed by Deutsche Telekom
            mMsrpEventListener.msrpTransferError(cpimMsgId, "error report " + statusCode,
                    typeMsrpChunk);
        }

        // Notify report transaction
        if (mReportTransaction != null) {
            mReportTransaction.notifyReport(statusCode, headers);
        }

        // Changed by Deutsche Telekom
        // Remove transaction info from list as transaction has reached a final state
        removeMsrpTransactionInfo(originalTransactionId);
    }

    // Changed by Deutsche Telekom
    /**
     * Set the control if is to map the msgId from transactionId if not present on received MSRP
     * messages
     * 
     * @param mapMsgIdFromTransationId
     */
    public void setMapMsgIdFromTransationId(boolean mapMsgIdFromTransationId) {
        if (mMapMsgIdFromTransationId != mapMsgIdFromTransationId) {
            synchronized (mTransactionMsgIdMapLock) {
                if (mapMsgIdFromTransationId) {
                    mTransactionInfoMap = new ConcurrentHashMap<String, MsrpSession.MsrpTransactionInfo>();
                    mMessageTransactionMap = new ConcurrentHashMap<String, String>();
                } else {
                    if (mTransactionInfoMap != null) {
                        mTransactionInfoMap.clear();
                        mTransactionInfoMap = null;
                    }
                    if (mMessageTransactionMap != null) {
                        mMessageTransactionMap.clear();
                        mMessageTransactionMap = null;
                    }
                }
            }
            mMapMsgIdFromTransationId = mapMsgIdFromTransationId;
        }
    }

    // Changed by Deutsche Telekom
    /**
     * Add transaction info item to list
     * 
     * @param transactionId MSRP transaction
     * @param msrpMsgId MSRP message ID
     * @param cpimMsgId CPIM message ID
     * @param typeMsrpChunk MSRP chunk type (see {@link TypeMsrpChunk})
     */
    private void addMsrpTransactionInfo(String transactionId, String msrpMsgId, String cpimMsgId,
            TypeMsrpChunk typeMsrpChunk) {
        if (mTransactionInfoMap != null && transactionId != null) {
            synchronized (mTransactionMsgIdMapLock) {
                mTransactionInfoMap.put(transactionId, new MsrpTransactionInfo(transactionId,
                        msrpMsgId, cpimMsgId, typeMsrpChunk));
                if (mMessageTransactionMap != null && msrpMsgId != null) {
                    mMessageTransactionMap.put(msrpMsgId, transactionId);
                }
            }
        }
    }

    // Changed by Deutsche Telekom
    /**
     * Remove transaction info item from list
     * 
     * @param transactionId
     */
    public void removeMsrpTransactionInfo(String transactionId) {
        if (mTransactionInfoMap != null && transactionId != null) {
            synchronized (mTransactionMsgIdMapLock) {
                if (mMessageTransactionMap != null) {
                    MsrpTransactionInfo transactionInfo = getMsrpTransactionInfo(transactionId);
                    if (transactionInfo != null && transactionInfo.mMsrpMsgId != null) {
                        mMessageTransactionMap.remove(transactionInfo.mMsrpMsgId);
                    }
                }
                mTransactionInfoMap.remove(transactionId);
            }
        }
    }

    // Changed by Deutsche Telekom
    /**
     * Get the transactions info
     */
    private MsrpTransactionInfo getMsrpTransactionInfo(String transactionId) {
        if (mTransactionInfoMap != null && transactionId != null) {
            synchronized (mTransactionMsgIdMapLock) {
                return mTransactionInfoMap.get(transactionId);
            }
        }

        return null;
    }

    // Changed by Deutsche Telekom
    /**
     * Get the transactions info for a specific MSRP message ID
     * 
     * @param msrpMsgId MSRP message ID
     */
    private MsrpTransactionInfo getMsrpTransactionInfoByMessageId(String msrpMsgId) {
        if (mMessageTransactionMap != null && mTransactionInfoMap != null && msrpMsgId != null) {
            synchronized (mTransactionMsgIdMapLock) {
                String transactionId = mMessageTransactionMap.get(msrpMsgId);
                if (transactionId != null) {
                    return mTransactionInfoMap.get(transactionId);
                }
            }
        }

        return null;
    }

    /**
     * Check the transactions info that have expired
     */
    public void checkMsrpTransactionInfo() {
        if (mTransactionInfoMap != null) {
            Thread checkThread = new Thread(new Runnable() {

                @Override
                public void run() {
                    List<MsrpTransactionInfo> msrpTransactionInfos = null;
                    synchronized (mTransactionMsgIdMapLock) {
                        // Copy the transaction info items to accelerate the locking while doing
                        // expiring process
                        msrpTransactionInfos = new ArrayList<MsrpTransactionInfo>(
                                mTransactionInfoMap.values());
                    }
                    for (MsrpTransactionInfo msrpTransactionInfo : msrpTransactionInfos) {
                        long delta = System.currentTimeMillis() - msrpTransactionInfo.mTimestamp;
                        if ((delta >= TRANSACTION_INFO_EXPIRY_PERIOD) || (delta < 0)) {
                            if (sLogger.isActivated()) {
                                sLogger.debug("Transaction info have expired (transactionId: "
                                        + msrpTransactionInfo.mTransactionId + ", msgId: "
                                        + msrpTransactionInfo.mMsrpMsgId + ")");
                            }
                            mTransactionInfoMap.remove(msrpTransactionInfo.mTransactionId);
                            if (mMessageTransactionMap != null) {
                                mMessageTransactionMap.remove(msrpTransactionInfo.mMsrpMsgId);
                            }
                        }
                    }

                }

            });
            checkThread.start();

        }
    }

    /**
     * Is established
     * 
     * @return true If the empty packet was sent successfully
     */
    public boolean isEstablished() {
        return mIsEstablished && !mCancelTransfer;
    }

}
