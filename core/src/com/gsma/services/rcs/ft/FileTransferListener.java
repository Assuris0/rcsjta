/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications AB.
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
 * NOTE: This file has been modified by Sony Mobile Communications AB.
 * Modifications are licensed under the License.
 ******************************************************************************/
package com.gsma.services.rcs.ft;

import android.net.Uri;


/**
 * File transfer event listener
 * 
 * @author Jean-Marc AUFFRET
 */
public abstract class FileTransferListener extends IFileTransferListener.Stub {
	/**
	 * Callback called when the file transfer is started
	 */
	public abstract void onTransferStarted();
	
	/**
	 * Callback called when the file transfer has been aborted
	 */
	public abstract void onTransferAborted();

	/**
	 * Callback called when the transfer has failed
	 * 
	 * @param error Error
	 * @see FileTransfer.Error
	 */
	public abstract void onTransferError(int error);
	
	/**
	 * Callback called during the transfer progress
	 * 
	 * @param currentSize Current transferred size in bytes
	 * @param totalSize Total size to transfer in bytes
	 */
	public abstract void onTransferProgress(long currentSize, long totalSize);

	/**
	 * Callback called when the file has been transferred
	 * 
	 * @param file URI of the transferred file transferred (local or a remote file)
	 */
	public abstract void onFileTransferred(Uri file);
}
