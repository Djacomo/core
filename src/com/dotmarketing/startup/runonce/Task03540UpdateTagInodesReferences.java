package com.dotmarketing.startup.runonce;

import java.util.List;
import java.util.Map;

import com.dotmarketing.business.APILocator;
import com.dotmarketing.common.db.DotConnect;
import com.dotmarketing.db.HibernateUtil;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.portlets.structure.model.Field;
import com.dotmarketing.startup.AbstractJDBCStartupTask;
import com.dotmarketing.tag.business.TagAPI;
import com.dotmarketing.tag.model.Tag;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;

/**
 * This class updates the contentlets tag fields references
 * in the TagInode table.
 *
 * TODO: Remove API calls.
 *
 * @author Oswaldo Gallango
 * @since 02/12/2016
 */
public class Task03540UpdateTagInodesReferences extends AbstractJDBCStartupTask {

	static String GET_STRUCTURES_WITH_TAGS_FIELDS="SELECT structure_inode, velocity_var_name, field_contentlet FROM field WHERE field_type=?";
	static String GET_CONTENT_HOST_ID="SELECT host_inode FROM identifier WHERE id=?";
	static String DELETE_OLD_CONTENT_TAG_INODES="DELETE FROM tag_inode WHERE inode=?";

	/**
	 * Update/fix the contentlets tags references in the tag_inode table 
	 */
	@Override
	public void executeUpgrade() throws DotDataException, DotRuntimeException  {

		Logger.info(this, "Starting Task03540UpdateTagInodesReferences depending on your dataset it could take several minutes.");

		TagAPI tagAPI = APILocator.getTagAPI();

		DotConnect dc = new DotConnect();
		dc.setSQL(GET_STRUCTURES_WITH_TAGS_FIELDS);
		dc.addParam(Field.FieldType.TAG.toString());
		List<Map<String, Object>> results = (List<Map<String, Object>>) dc.loadResults();

		for(Map<String, Object> result: results){

			final String structureInode = (String)result.get("structure_inode");
			final String field_varname = (String)result.get("velocity_var_name");
			final String field_contentlet = (String)result.get("field_contentlet");

			//We are going to retrieve only set of 25 rows. Avoiding hundreds or thousands of results at once.
			final int selectMaxRows = 25;
			int selectStartRow = 0;

			//Get contents with that field set.
			dc = new DotConnect();
			dc.setSQL("SELECT inode, identifier, " + field_contentlet +
					" FROM contentlet" +
					" WHERE structure_inode=?" +
					" AND " + field_contentlet + " IS NOT NULL");
			dc.addParam(structureInode);
			dc.setStartRow(selectStartRow);
			dc.setMaxRows(selectMaxRows);
			List<Map<String, Object>> contentResults = (List<Map<String, Object>>) dc.loadResults();

			while (contentResults != null && !contentResults.isEmpty()) {

				for(Map<String, Object> content : contentResults){
					String content_inode = (String)content.get("inode");
					String content_identifier = (String)content.get("identifier");
					String tags = (String)content.get(field_contentlet);

					if(UtilMethods.isSet(tags)){
						//get content HostId
						dc = new DotConnect();
						dc.setSQL(GET_CONTENT_HOST_ID);
						dc.addObject(content_identifier);
						List<Map<String,Object>> identifier = (List<Map<String, Object>>) dc.loadResults();
						String hostId = (String)identifier.get(0).get("host_inode");

						try{
							HibernateUtil.startTransaction();

							//delete old contents tag inodes
							dc = new DotConnect();
							dc.setSQL(DELETE_OLD_CONTENT_TAG_INODES);
							dc.addObject(content_inode);
							dc.loadResult();

							//Get/Create the tags
							List<Tag> list = tagAPI.getTagsInText(tags, hostId);
							for ( Tag tag : list ) {

								Logger.info(this, "Adding Contentlet Tag Inode: " + tag.getTagName());

								//Relate the found/created tag with this contentlet
								tagAPI.addContentletTagInode(tag, content_inode, field_varname);
							}

							//clean contentlet tag field
							dc = new DotConnect();
							dc.setSQL("UPDATE contentlet SET "+ field_contentlet +"='' WHERE inode=?");
							dc.addParam(content_inode);
							dc.loadResult();
							HibernateUtil.commitTransaction();

						} catch(DotSecurityException e){
							HibernateUtil.rollbackTransaction();
						}
					}
				}

				Logger.info(this, "Fetching more tags from the DB, please wait...");

				//Increase start row now that we completed the work with the latest resultset.
				selectStartRow += selectMaxRows;

				dc = new DotConnect();
				dc.setSQL("SELECT inode, identifier, " + field_contentlet +
						" FROM contentlet" +
						" WHERE structure_inode=?" +
						" AND " + field_contentlet + " IS NOT NULL");
				dc.addParam(structureInode);
				dc.setStartRow(selectStartRow);
				dc.setMaxRows(selectMaxRows);
				contentResults = (List<Map<String, Object>>) dc.loadResults();
			}
		}

		Logger.info(this, "Finishing Task03540UpdateTagInodesReferences");
	}

	@Override
	public boolean forceRun() {
		return true;
	}

	@Override
	public String getPostgresScript() {
		return null;
	}

	@Override
	public String getMySQLScript() {
		return null;
	}

	@Override
	public String getOracleScript() {
		return null;
	}

	@Override
	public String getMSSQLScript() {
		return null;
	}

	@Override
	public String getH2Script() {
		return null;
	}

	@Override
	protected List<String> getTablesToDropConstraints() {
		return null;
	}

}
