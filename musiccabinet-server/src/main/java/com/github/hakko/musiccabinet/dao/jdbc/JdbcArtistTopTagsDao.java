package com.github.hakko.musiccabinet.dao.jdbc;

import static com.github.hakko.musiccabinet.domain.model.library.WebserviceInvocation.Calltype.ARTIST_GET_TOP_TAGS;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.BatchSqlUpdate;

import com.github.hakko.musiccabinet.dao.ArtistTopTagsDao;
import com.github.hakko.musiccabinet.domain.model.music.Artist;
import com.github.hakko.musiccabinet.domain.model.music.Tag;

public class JdbcArtistTopTagsDao implements ArtistTopTagsDao, JdbcTemplateDao {

	private JdbcTemplate jdbcTemplate;
	
	public void setDataSource(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@Override
	public void createTopTags(Artist artist, List<Tag> tags) {
		if (tags.size() > 0) {
			clearImportTable();
			batchInsert(artist, tags);
			updateTopTags();
		}
	}
	
	private void clearImportTable() {
		jdbcTemplate.execute("delete from music.artisttoptag_import");
	}

	private void batchInsert(Artist artist, List<Tag> tags) {
		int sourceArtistId = jdbcTemplate.queryForInt(
				"select * from music.get_artist_id(?)", artist.getName());
		
		String sql = "insert into music.artisttoptag_import (artist_id, tag_name, tag_count) values (?,?,?)";
		BatchSqlUpdate batchUpdate = new BatchSqlUpdate(jdbcTemplate.getDataSource(), sql);
		batchUpdate.setBatchSize(1000);
		batchUpdate.declareParameter(new SqlParameter("artist_id", Types.INTEGER));
		batchUpdate.declareParameter(new SqlParameter("tag_name", Types.VARCHAR));
		batchUpdate.declareParameter(new SqlParameter("tag_count", Types.SMALLINT));
		
		for (Tag tag : tags) {
			batchUpdate.update(new Object[]{sourceArtistId, tag.getName(), tag.getCount()});
		}
		batchUpdate.flush();
	}

	private void updateTopTags() {
		jdbcTemplate.execute("select music.update_artisttoptag_from_import()");
	}
	
	@Override
	public List<Tag> getTopTags(Artist artist) {
		final int artistId = jdbcTemplate.queryForInt(
				"select * from music.get_artist_id(?)", artist.getName());
		
		String sql = "select tag_name, tag_count"
			+ " from music.artisttoptag att" 
			+ " inner join music.tag tag on att.tag_id = tag.id"
			+ " where att.artist_id = ? order by att.tag_count";
		
		List<Tag> topTags = jdbcTemplate.query(sql, 
				new Object[]{artistId}, new RowMapper<Tag>() {
			@Override
			public Tag mapRow(ResultSet rs, int rowNum)
					throws SQLException {
				String tagName = rs.getString(1);
				short tagCount = rs.getShort(2);
				return new Tag(tagName, tagCount);
			}
		});
		
		return topTags;
	}

	@Override
	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	@Override
	public List<Artist> getArtistsWithoutTopTags() {
		String sql = "select artist_name_capitalization from music.artist where id in ("
				+ " select distinct t.artist_id from library.musicfile mf"
				+ " inner join music.track t on mf.track_id = t.id"
				+ " where not exists ("
				+ " select 1 from music.artisttoptag where artist_id = t.artist_id)"
				+ " and not exists ("
				+ " select 1 from library.webservice_history where artist_id = t.artist_id "
				+ " and calltype_id = " + ARTIST_GET_TOP_TAGS.getDatabaseId() + "))";
		
		List<Artist> artists = jdbcTemplate.query(sql, new RowMapper<Artist>() {
			@Override
			public Artist mapRow(ResultSet rs, int rowNum) throws SQLException {
				return new Artist(rs.getString(1));
			}
		});
		
		return artists;
	}

}