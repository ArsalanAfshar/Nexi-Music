package com.nexi.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlayerCollectionsTest {
    @Test fun playlistRoundTripPreservesOrderAndDuplicateTrackMembership() {
        val playlists = listOf(SavedPlaylist("Road trip", listOf(4, 8, 4)), SavedPlaylist("Quiet", emptyList()))
        assertEquals(playlists, PlaylistCodec.decode(PlaylistCodec.encode(playlists)))
    }

    @Test fun rejectsMalformedNamesAndDuplicatePlaylistNames() {
        assertThrows(IllegalArgumentException::class.java) { PlaylistCodec.validateName("  ") }
        assertThrows(IllegalArgumentException::class.java) { PlaylistCodec.validateName(" bad\nname ") }
        val duplicate = """{"format":"nexi-playlists","version":1,"playlists":[{"name":"Mix","tracks":[]},{"name":"mix","tracks":[]}] }"""
        assertThrows(IllegalArgumentException::class.java) { PlaylistCodec.decode(duplicate) }
    }

    @Test fun rejectsInvalidTrackIdsAndUnsupportedVersions() {
        val badId = """{"format":"nexi-playlists","version":1,"playlists":[{"name":"Mix","tracks":[0]}]}"""
        val unsupported = """{"format":"nexi-playlists","version":2,"playlists":[]}"""
        assertThrows(IllegalArgumentException::class.java) { PlaylistCodec.decode(badId) }
        assertThrows(IllegalArgumentException::class.java) { PlaylistCodec.decode(unsupported) }
    }

    @Test fun addNextMoveAndRemovePreserveQueueSemantics() {
        assertEquals(listOf(1L, 4L, 5L, 2L, 3L), QueueOperations.addNext(listOf(1, 2, 3), listOf(4, 5), 0))
        assertEquals(listOf(1L, 3L, 2L), QueueOperations.move(listOf(1, 2, 3), 1, 2))
        assertEquals(listOf(1L, 3L), QueueOperations.remove(listOf(1, 2, 3), 1))
    }
}
