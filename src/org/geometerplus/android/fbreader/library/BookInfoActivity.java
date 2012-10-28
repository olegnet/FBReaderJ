/*
 * Copyright (C) 2010-2012 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.library;

import java.io.File;
import java.text.DateFormat;
import java.util.*;

import android.app.Activity;
import android.app.ActionBar;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;

import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.filesystem.ZLPhysicalFile;
import org.geometerplus.zlibrary.core.image.ZLImage;
import org.geometerplus.zlibrary.core.image.ZLLoadableImage;
import org.geometerplus.zlibrary.core.resources.ZLResource;
import org.geometerplus.zlibrary.core.language.ZLLanguageUtil;

import org.geometerplus.zlibrary.ui.android.R;
import org.geometerplus.zlibrary.ui.android.image.ZLAndroidImageData;
import org.geometerplus.zlibrary.ui.android.image.ZLAndroidImageManager;

import org.geometerplus.fbreader.library.*;
import org.geometerplus.fbreader.network.HtmlUtil;

import org.geometerplus.android.fbreader.FBReader;
import org.geometerplus.android.fbreader.FBUtil;
import org.geometerplus.android.fbreader.preferences.EditBookInfoActivity;

public class BookInfoActivity extends Activity implements MenuItem.OnMenuItemClickListener {
	private static final boolean ENABLE_EXTENDED_FILE_INFO = false;

	public static final String CURRENT_BOOK_PATH_KEY = "CurrentBookPath";
	public static final String FROM_READING_MODE_KEY = "fromReadingMode";

	private final ZLResource myResource = ZLResource.resource("bookInfo");
	private ZLFile myFile;
	private int myResult;
	private boolean myDontReloadBook;

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		Thread.setDefaultUncaughtExceptionHandler(
			new org.geometerplus.zlibrary.ui.android.library.UncaughtExceptionHandler(this)
		);

		final Intent intent = getIntent();
		final String path = intent.getStringExtra(CURRENT_BOOK_PATH_KEY);
		myDontReloadBook = intent.getBooleanExtra(FROM_READING_MODE_KEY, false);
		myFile = ZLFile.createFileByPath(path);

		if (SQLiteBooksDatabase.Instance() == null) {
			new SQLiteBooksDatabase(this, "LIBRARY");
		}

		final ActionBar bar = getActionBar();
		if (bar != null) {
			bar.setDisplayShowTitleEnabled(false);
		}
		setContentView(R.layout.book_info);

		myResult = FBReader.RESULT_DO_NOTHING;
		setResult(myResult, intent);
	}

	@Override
	protected void onStart() {
		super.onStart();

		final Book book = Book.getByFile(myFile);

		if (book != null) {
			// we do force language & encoding detection
			book.getEncoding();

			setupCover(book);
			setupBookInfo(book);
			setupAnnotation(book);
			setupFileInfo(book);
		}

		final View root = findViewById(R.id.book_info_root);
		root.invalidate();
		root.requestLayout();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		final Book book = Book.getByFile(myFile);
		if (book != null) {
			setupBookInfo(book);
			myDontReloadBook = false;
		}

		myResult = Math.max(myResult, resultCode);
		setResult(myResult);
	}

	private Button findButton(int buttonId) {
		return (Button)findViewById(buttonId);
	}

	private void setupInfoPair(int id, String key, CharSequence value) {
		setupInfoPair(id, key, value, 0);
	}

	private void setupInfoPair(int id, String key, CharSequence value, int param) {
		final LinearLayout layout = (LinearLayout)findViewById(id);
		if (value == null || value.length() == 0) {
			layout.setVisibility(View.GONE);
			return;
		}
		layout.setVisibility(View.VISIBLE);
		((TextView)layout.findViewById(R.id.book_info_key)).setText(myResource.getResource(key).getValue(param));
		((TextView)layout.findViewById(R.id.book_info_value)).setText(value);
	}

	private void setupCover(Book book) {
		final ImageView coverView = (ImageView)findViewById(R.id.book_cover);

		final DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		final int maxHeight = metrics.heightPixels * 2 / 3;
		final int maxWidth = maxHeight * 2 / 3;

		coverView.setVisibility(View.GONE);
		coverView.setImageDrawable(null);

		final ZLImage image = LibraryUtil.getCover(book);

		if (image == null) {
			return;
		}

		if (image instanceof ZLLoadableImage) {
			final ZLLoadableImage loadableImage = (ZLLoadableImage)image;
			if (!loadableImage.isSynchronized()) {
				loadableImage.synchronize();
			}
		}
		final ZLAndroidImageData data =
			((ZLAndroidImageManager)ZLAndroidImageManager.Instance()).getImageData(image);
		if (data == null) {
			return;
		}

		final Bitmap coverBitmap = data.getBitmap(2 * maxWidth, 2 * maxHeight);
		if (coverBitmap == null) {
			return;
		}

		coverView.setVisibility(View.VISIBLE);
		coverView.getLayoutParams().width = maxWidth;
		coverView.getLayoutParams().height = maxHeight;
		coverView.setImageBitmap(coverBitmap);
	}

	private void setupBookInfo(Book book) {
		((TextView)findViewById(R.id.book_info_title)).setText(myResource.getResource("bookInfo").getValue());

		setupInfoPair(R.id.book_title, "title", book.getTitle());

		final StringBuilder buffer = new StringBuilder();
		final List<Author> authors = book.authors();
		for (Author a : authors) {
			if (buffer.length() > 0) {
				buffer.append(", ");
			}
			buffer.append(a.DisplayName);
		}
		setupInfoPair(R.id.book_authors, "authors", buffer, authors.size());

		final SeriesInfo series = book.getSeriesInfo();
		setupInfoPair(R.id.book_series, "series", series == null ? null : series.Name);
		String seriesIndexString = null;
		if (series != null && series.Index != null) {
			seriesIndexString = series.Index.toString();
		}
		setupInfoPair(R.id.book_series_index, "indexInSeries", seriesIndexString);

		buffer.delete(0, buffer.length());
		final HashSet<String> tagNames = new HashSet<String>();
		for (Tag tag : book.tags()) {
			if (!tagNames.contains(tag.Name)) {
				if (buffer.length() > 0) {
					buffer.append(", ");
				}
				buffer.append(tag.Name);
				tagNames.add(tag.Name);
			}
		}
		setupInfoPair(R.id.book_tags, "tags", buffer, tagNames.size());
		String language = book.getLanguage();
		if (!ZLLanguageUtil.languageCodes().contains(language)) {
			language = ZLLanguageUtil.OTHER_LANGUAGE_CODE;
		}
		setupInfoPair(R.id.book_language, "language", ZLLanguageUtil.languageName(language));
	}

	private void setupAnnotation(Book book) {
		final TextView titleView = (TextView)findViewById(R.id.book_info_annotation_title);
		final TextView bodyView = (TextView)findViewById(R.id.book_info_annotation_body);
		final String annotation = LibraryUtil.getAnnotation(book);
		if (annotation == null) {
			titleView.setVisibility(View.GONE);
			bodyView.setVisibility(View.GONE);
		} else {
			titleView.setText(myResource.getResource("annotation").getValue());
			bodyView.setText(HtmlUtil.getHtmlText(annotation));
			bodyView.setMovementMethod(new LinkMovementMethod());
			bodyView.setTextColor(ColorStateList.valueOf(bodyView.getTextColors().getDefaultColor()));
		}
	}

	private void setupFileInfo(Book book) {
		((TextView)findViewById(R.id.file_info_title)).setText(myResource.getResource("fileInfo").getValue());

		setupInfoPair(R.id.file_name, "name", book.File.getPath());
		if (ENABLE_EXTENDED_FILE_INFO) {
			setupInfoPair(R.id.file_type, "type", book.File.getExtension());

			final ZLPhysicalFile physFile = book.File.getPhysicalFile();
			final File file = physFile == null ? null : physFile.javaFile();
			if (file != null && file.exists() && file.isFile()) {
				setupInfoPair(R.id.file_size, "size", formatSize(file.length()));
				setupInfoPair(R.id.file_time, "time", formatDate(file.lastModified()));
			} else {
				setupInfoPair(R.id.file_size, "size", null);
				setupInfoPair(R.id.file_time, "time", null);
			}
		} else {
			setupInfoPair(R.id.file_type, "type", null);
			setupInfoPair(R.id.file_size, "size", null);
			setupInfoPair(R.id.file_time, "time", null);
		}
	}

	private String formatSize(long size) {
		if (size <= 0) {
			return null;
		}
		final int kilo = 1024;
		if (size < kilo) { // less than 1 kilobyte
			return myResource.getResource("sizeInBytes").getValue((int)size).replaceAll("%s", String.valueOf(size));
		}
		final String value;
		if (size < kilo * kilo) { // less than 1 megabyte
			value = String.format("%.2f", ((float)size) / kilo);
		} else {
			value = String.valueOf(size / kilo);
		}
		return myResource.getResource("sizeInKiloBytes").getValue().replaceAll("%s", value);
	}

	private String formatDate(long date) {
		if (date == 0) {
			return null;
		}
		return DateFormat.getDateTimeInstance().format(new Date(date));
	}

	private static final int OPEN_BOOK = 1;
	private static final int EDIT_INFO = 2;
	private static final int SHARE_BOOK = 3;
	private static final int RELOAD_INFO = 4;

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		addMenuItem(menu, OPEN_BOOK, "openBook", true);
		addMenuItem(menu, EDIT_INFO, "editInfo", true);
		addMenuItem(menu, SHARE_BOOK, "shareBook", false);
		addMenuItem(menu, RELOAD_INFO, "reloadInfo", false);
		return true;
	}

	private void addMenuItem(Menu menu, int index, String resourceKey, boolean showAsAction) {
		final String label =
			ZLResource.resource("dialog").getResource("button").getResource(resourceKey).getValue();
		final MenuItem item = menu.add(0, index, Menu.NONE, label);
		item.setShowAsAction(
			showAsAction ? MenuItem.SHOW_AS_ACTION_IF_ROOM : MenuItem.SHOW_AS_ACTION_NEVER
		);
		item.setOnMenuItemClickListener(this);
	}

	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
			case OPEN_BOOK:
				if (myDontReloadBook) {
					finish();
				} else {
					startActivity(
						new Intent(getApplicationContext(), FBReader.class)
							.setAction(Intent.ACTION_VIEW)
							.putExtra(FBReader.BOOK_PATH_KEY, myFile.getPath())
							.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
					);
				}
				return true;
			case EDIT_INFO:
				startActivityForResult(
					new Intent(getApplicationContext(), EditBookInfoActivity.class)
						.putExtra(CURRENT_BOOK_PATH_KEY, myFile.getPath()),
					1
				);
				return true;
			case SHARE_BOOK:
				FBUtil.shareBook(this, Book.getByFile(myFile));
				return true;
			case RELOAD_INFO:
			{
				final Book book = Book.getByFile(myFile);
				if (book != null) {
					book.reloadInfoFromFile();
					setupBookInfo(book);
					myResult = Math.max(myResult, FBReader.RESULT_RELOAD_BOOK);
					setResult(myResult);
				}
				return true;
			}
			default:
				return true;
		}
	}
}
