package org.gsma.joyn.vsh;

import org.gsma.joyn.vsh.IVideoSharing;
import org.gsma.joyn.vsh.IVideoSharingListener;
import org.gsma.joyn.vsh.INewVideoSharingListener;
import org.gsma.joyn.vsh.IVideoPlayer;

/**
 * Video sharing service API
 */
interface IVideoSharingService {
	List<IBinder> getVideoSharings();
	
	IVideoSharing getVideoSharing(in String sharingId);

	IVideoSharing shareVideo(in String contact, in IVideoPlayer player, in IVideoSharingListener listener);
	
	void addNewVideoSharingListener(in INewVideoSharingListener listener);

	void removeNewVideoSharingListener(in INewVideoSharingListener listener);
}