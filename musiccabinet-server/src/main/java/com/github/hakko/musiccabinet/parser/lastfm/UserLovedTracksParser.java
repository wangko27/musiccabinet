package com.github.hakko.musiccabinet.parser.lastfm;

import java.util.List;

import com.github.hakko.musiccabinet.domain.model.music.Track;

public interface UserLovedTracksParser {

	List<Track> getLovedTracks();
	short getPage();
	short getTotalPages();
	
}
