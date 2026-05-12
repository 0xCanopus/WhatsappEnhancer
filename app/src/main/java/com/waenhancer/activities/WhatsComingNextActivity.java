package com.waenhancer.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.waenhancer.R;
import com.waenhancer.activities.base.BaseActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.noties.markwon.Markwon;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WhatsComingNextActivity extends BaseActivity {
    private static final String TAG = "WhatsComingNext";
    private static final String BETA_CHANGELOG_URL = "https://raw.githubusercontent.com/mubashardev/WaEnhancerX/refs/heads/beta/changelog.txt";

    private com.google.android.material.loadingindicator.LoadingIndicator loadingProgress;
    private androidx.core.widget.NestedScrollView scrollView;
    private com.google.android.material.textview.MaterialTextView tvChangelogBody;
    private android.widget.LinearLayout changelogItemsContainer;
    private Markwon markwon;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_whats_coming_next);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        loadingProgress = findViewById(R.id.loading_progress);
        scrollView = findViewById(R.id.scroll_view);
        tvChangelogBody = findViewById(R.id.tv_changelog_body);
        changelogItemsContainer = findViewById(R.id.changelog_items_container);

        markwon = Markwon.create(this);

        fetchBetaChangelog();
    }

    private void fetchBetaChangelog() {
        loadingProgress.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);

        CompletableFuture.runAsync(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(BETA_CHANGELOG_URL)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    runOnUiThread(() -> {
                        loadingProgress.setVisibility(View.GONE);
                        scrollView.setVisibility(View.VISIBLE);
                        renderChangelog(body);
                    });
                } else {
                    throw new IOException("Unexpected code " + response);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch beta changelog", e);
                runOnUiThread(() -> {
                    loadingProgress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void renderChangelog(String body) {
        changelogItemsContainer.removeAllViews();
        List<ParsedCategory> parsedCategories = parseBody(body);
        if (parsedCategories.isEmpty()) {
            tvChangelogBody.setVisibility(View.VISIBLE);
            markwon.setMarkdown(tvChangelogBody, body.trim());
        } else {
            tvChangelogBody.setVisibility(View.GONE);
            LayoutInflater inflater = LayoutInflater.from(this);
            for (ParsedCategory category : parsedCategories) {
                // 1. Inflate Category Header Row
                View headerRow = inflater.inflate(R.layout.item_changelog_row, changelogItemsContainer, false);
                com.google.android.material.textview.MaterialTextView tvCatBadge = headerRow.findViewById(R.id.tv_item_badge);
                com.google.android.material.textview.MaterialTextView tvCatText = headerRow.findViewById(R.id.tv_item_text);

                tvCatText.setVisibility(View.GONE);
                tvCatBadge.setText(category.name.toUpperCase(java.util.Locale.US));

                // Style the category badge based on category name
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                gd.setCornerRadius(dpToPx(6));

                int bgColor;
                if ("added".equalsIgnoreCase(category.name)) {
                    bgColor = 0xFF10B981; // Emerald Green
                } else if ("improvements".equalsIgnoreCase(category.name)) {
                    bgColor = 0xFF3B82F6; // Vibrant Blue
                } else if ("fixes".equalsIgnoreCase(category.name)) {
                    bgColor = 0xFFEF4444; // Coral Red
                } else {
                    bgColor = 0xFF64748B; // Slate Gray
                }
                gd.setColor(bgColor);
                tvCatBadge.setBackground(gd);
                tvCatBadge.setTextColor(0xFFFFFFFF);

                changelogItemsContainer.addView(headerRow);

                // 2. Inflate Category Bullet Point Rows
                for (String itemText : category.items) {
                    View itemRow = inflater.inflate(R.layout.item_changelog_row, changelogItemsContainer, false);
                    com.google.android.material.textview.MaterialTextView tvItemBadge = itemRow.findViewById(R.id.tv_item_badge);
                    com.google.android.material.textview.MaterialTextView tvItemText = itemRow.findViewById(R.id.tv_item_text);

                    tvItemBadge.setVisibility(View.GONE);
                    tvItemText.setText("•  " + itemText);

                    android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) tvItemText.getLayoutParams();
                    lp.leftMargin = dpToPx(16);
                    tvItemText.setLayoutParams(lp);

                    changelogItemsContainer.addView(itemRow);
                }
            }
        }
    }

    private static class ParsedCategory {
        final String name;
        final List<String> items = new ArrayList<>();

        ParsedCategory(String name) {
            this.name = name;
        }
    }

    private static List<ParsedCategory> parseBody(String body) {
        List<ParsedCategory> categories = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) {
            return categories;
        }
        String[] lines = body.split("\n");
        ParsedCategory currentCategory = null;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // Check if the line is a category header, e.g., [Added], [Fixes], [Improvements]
            if (line.startsWith("[") && line.endsWith("]")) {
                String catName = line.substring(1, line.length() - 1).trim();
                currentCategory = findOrCreateCategory(categories, catName);
                continue;
            }

            String text = line;
            if (text.startsWith("-") || text.startsWith("*")) {
                text = text.substring(1).trim();
            }

            String itemCategoryName = (currentCategory != null) ? currentCategory.name : "Added";
            if (text.startsWith("[")) {
                int closeBracket = text.indexOf(']');
                if (closeBracket > 0) {
                    itemCategoryName = text.substring(1, closeBracket).trim();
                    text = text.substring(closeBracket + 1).trim();
                }
            }

            if (text.startsWith("-") || text.startsWith("*")) {
                text = text.substring(1).trim();
            }

            if (!text.isEmpty()) {
                ParsedCategory cat = findOrCreateCategory(categories, itemCategoryName);
                cat.items.add(text);
            }
        }
        return categories;
    }

    private static ParsedCategory findOrCreateCategory(List<ParsedCategory> categories, String name) {
        for (ParsedCategory cat : categories) {
            if (cat.name.equalsIgnoreCase(name)) {
                return cat;
            }
        }
        ParsedCategory newCat = new ParsedCategory(name);
        categories.add(newCat);
        return newCat;
    }

    private int dpToPx(int dp) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
