/*
 * chatterbox - a (not so helpful) Discord bot custom written for a private server
 * Copyright (C) 2021 Ryan Morrison
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.ryanmorrison.chatterbox.command;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.ryanmorrison.chatterbox.jukebox.GuildMusicManager;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Command for interacting with chatterbox's Jukebox, enabling playback of certain audio streams in voice
 * channels.
 *
 * @author Ryan Morrison
 * @since 1.0
 */
@Singleton
public class JukeboxCommand extends Command {

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public JukeboxCommand() {
        this.name = "jukebox";
        this.help = "play music (or audio) of your choice in a voice channel";
        this.helpBiConsumer = (event, command) -> {
            MessageBuilder builder = new MessageBuilder()
                    .append("jukebox", MessageBuilder.Formatting.BOLD)
                    .append(" - turn a voice channel into a dance party!\n\n")
                    .append("play <URL>", MessageBuilder.Formatting.BOLD)
                    .append(" - plays the given URL (YouTube/Vimeo/Twitch stream) in your voice channel, or queues it " +
                            "if another track is playing.\n")
                    .append("skip", MessageBuilder.Formatting.BOLD)
                    .append(" - skips to the next track, if it exists in the playback queue.\n")
                    .append("peek", MessageBuilder.Formatting.BOLD)
                    .append(" - displays the next upcoming track, if it exists in the playback queue.\n")
                    .append("pause", MessageBuilder.Formatting.BOLD)
                    .append(" - pauses the currently playing track.\n")
                    .append("resume", MessageBuilder.Formatting.BOLD)
                    .append(" - resumes a paused track.\n")
                    .append("cancel", MessageBuilder.Formatting.BOLD)
                    .append(" - stops playback of any tracks and clears all items from the playback queue.\n")
                    .append("leave", MessageBuilder.Formatting.BOLD)
                    .append(" - clears all items from the playback queue and leave the channel.\n\n")
                    .append("You must be in a voice channel to add tracks to the jukebox or change tracks. The bot " +
                            "will join and play tracks in the voice channel you're currently in.\n\n")
                    .append("Try it! Join a voice channel and write: ")
                    .append(event.getClient().getPrefix() + this.getName() + " play " +
                            "https://www.youtube.com/watch?v=DO7Y_Kw4LzU", MessageBuilder.Formatting.BOLD);

            event.reply(builder.build());
        };
        this.guildOnly = true;
        this.musicManagers = new HashMap<>();
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    @Override
    protected void execute(CommandEvent event) {
        String[] args = event.getArgs().split(" ");
        String action = args[0];

        Optional<VoiceChannel> voiceChannel = getVoiceChannel(event.getGuild(), event.getAuthor());
        if (voiceChannel.isEmpty()) {
            event.replyError("You must be in a voice channel to execute any Jukebox commands!");
            return;
        }

        if (action == null || action.trim().equals("")) {
            event.reply(new MessageBuilder()
                    .append("You need to provide an action for the Jukebox to perform! Try ")
                    .append(event.getClient().getPrefix())
                    .append(this.getName())
                    .append(" help to see a list of possible options and examples.")
                    .build());

            return;
        }

        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        if (musicManager.getVoiceChannel() != null) {
            if (musicManager.getVoiceChannel().getIdLong() != voiceChannel.get().getIdLong()) {
                event.replyError("I'm already in another voice channel in this server! I'll have to be asked to leave " +
                        "that voice channel before I can join this one.");
                return;
            }
        }

        if (action.equalsIgnoreCase("play")) {
            handlePlay(event, voiceChannel.get(), args[1]);
        }
        else if (action.equalsIgnoreCase("skip")) {
            handleSkip(event);
        }
        else if (action.equalsIgnoreCase("peek")) {
            handlePeek(event);
        }
        else if (action.equalsIgnoreCase("pause")) {
            handlePauseResume(event, true);
        }
        else if (action.equalsIgnoreCase("resume")) {
            handlePauseResume(event, false);
        }
        else if (action.equalsIgnoreCase("cancel")) {
            handleCancel(event);
        }
        else if (action.equalsIgnoreCase("leave")) {
            handleLeave(event);
        }
        else {
            event.reply(new MessageBuilder()
                    .append("This isn't a valid action for the Jukebox! Try ")
                    .append(event.getClient().getPrefix())
                    .append(this.getName())
                    .append(" help to see a list of possible options and examples.")
                    .build());
        }
    }

    private void handlePlay(final CommandEvent event, final VoiceChannel voiceChannel, final String url) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        boolean connectedAlready = musicManager.getVoiceChannel() != null;
        String queueLine = connectedAlready ? ":arrow_right_hook: Added to queue: " : ":arrow_right_hook: " +
                "Joining voice channel \"" + voiceChannel.getName() + "\" and adding to queue: ";

        this.playerManager.loadItemOrdered(musicManager, url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                event.reply(new MessageBuilder()
                        .append(queueLine)
                        .append(track.getInfo().title, MessageBuilder.Formatting.ITALICS)
                        .build());
                play(event.getGuild(), voiceChannel, musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();
                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                event.reply(new MessageBuilder()
                        .append(queueLine)
                        .append(firstTrack.getInfo().title, MessageBuilder.Formatting.ITALICS)
                        .append(" (first track of playlist ")
                        .append(playlist.getName(), MessageBuilder.Formatting.ITALICS)
                        .append(")")
                        .build());

                play(event.getGuild(), voiceChannel, musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                event.replyError("Couldn't find media to play in URL " + url + ", make sure it is a valid media source?");
            }

            @Override
            public void loadFailed(FriendlyException e) {
                event.replyError("Could not play media due to error: " + e.getMessage());
            }
        });
    }

    private void handleSkip(CommandEvent event) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        AudioTrack nextTrack = musicManager.getScheduler().next();

        if (nextTrack != null) {
            event.reply(new MessageBuilder()
                    .append(":arrow_forward: Now playing: ")
                    .append(nextTrack.getInfo().title, MessageBuilder.Formatting.ITALICS)
                    .build());
            return;
        }

        event.replyWarning("There are no more tracks in the queue!");
    }

    private void handlePeek(CommandEvent event) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        AudioTrack nextTrack = musicManager.getScheduler().peek();

        if (nextTrack != null) {
            event.reply(new MessageBuilder()
                    .append(":arrow_forward: Up next: ")
                    .append(nextTrack.getInfo().title, MessageBuilder.Formatting.ITALICS)
                    .build());
            return;
        }

        event.replyWarning("There are no more tracks in the queue!");
    }

    private void handlePauseResume(CommandEvent event, boolean shouldPause) {
        String action = shouldPause ? "pause" : "resume";

        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        AudioPlayer player = musicManager.getPlayer();
        AudioTrack playingTrack = player.getPlayingTrack();

        if (playingTrack == null) {
            event.replyError("Nothing's currently playing for me to " + action + "!");
            return;
        }

        if (shouldPause && player.isPaused()) {
            event.replyError("Can't pause - a track is already paused.");
        }
        else if (!shouldPause && !player.isPaused()) {
            event.replyError("Can't resume = a track is already playing.");
        }

        musicManager.getPlayer().setPaused(shouldPause);
        event.getMessage().addReaction("U+1F44D").queue(); // thumbs up emoji
    }

    private void handleCancel(CommandEvent event) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        AudioTrack playingTrack = musicManager.getPlayer().getPlayingTrack();

        if (playingTrack == null) {
            event.replyError("Nothing's currently playing for me to clear!");
            return;
        }

        musicManager.getPlayer().destroy();
        musicManager.getScheduler().clear();
        event.getMessage().addReaction("U+1F44D").queue(); // thumbs up emoji
    }

    private void handleLeave(CommandEvent event) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        VoiceChannel voiceChannel = musicManager.getVoiceChannel();
        if (voiceChannel != null) {
            musicManager.getPlayer().destroy();
            event.getGuild().getAudioManager().closeAudioConnection();
            this.musicManagers.remove(event.getGuild().getIdLong());
            event.getMessage().addReaction("U+1F44D").queue(); // thumbs up emoji
            return;
        }

        event.replyWarning("I can't leave the voice channel, I'm not in one currently!");
    }

    private Optional<VoiceChannel> getVoiceChannel(Guild guild, User author) {
        for (VoiceChannel currentChannel : guild.getVoiceChannels()) {
            boolean matches = currentChannel.getMembers().stream()
                    .map(Member::getUser)
                    .anyMatch(user -> user.getIdLong() == author.getIdLong());

            if (matches) {
                return Optional.of(currentChannel);
            }
        }

        return Optional.empty();
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        GuildMusicManager musicManager = this.musicManagers.get(guild.getIdLong());
        if (musicManager == null) {
            musicManager = new GuildMusicManager(this.playerManager);
            this.musicManagers.put(guild.getIdLong(), musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private void play(Guild guild, VoiceChannel voiceChannel, GuildMusicManager musicManager, AudioTrack track) {
        guild.getAudioManager().openAudioConnection(voiceChannel);
        musicManager.setVoiceChannel(voiceChannel);
        musicManager.getScheduler().queue(track);
    }
}
