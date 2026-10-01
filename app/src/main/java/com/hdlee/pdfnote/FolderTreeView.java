package com.hdlee.pdfnote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.io.File;
import java.util.*;

/** A navigable folder tree, shared by the document shelf and copy/move picker. */
final class FolderTreeView extends ScrollView {
    interface Selected {void folder(File folder);}
    private final LibraryRepository repository;
    private final LinearLayout rows;
    private final Set<String> expanded=new HashSet<>();
    private File selected;
    private final Selected listener;
    FolderTreeView(Context context,LibraryRepository repository,File selected,Selected listener){
        super(context);this.repository=repository;this.listener=listener;this.selected=selected;
        rows=new LinearLayout(context);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(6),dp(8),dp(6),dp(8));addView(rows,new LayoutParams(-1,-2));setBackgroundColor(0xFFF1F4F8);
        for(File parent=selected;parent!=null;parent=parent.getParentFile()){expanded.add(parent.getAbsolutePath());if(parent.equals(repository.root))break;}reload();
    }
    File selected(){return selected;}
    void select(File folder){selected=folder;for(File parent=folder;parent!=null;parent=parent.getParentFile()){expanded.add(parent.getAbsolutePath());if(parent.equals(repository.root))break;}reload();}
    void reload(){rows.removeAllViews();addFolder(repository.root,0);}
    private void addFolder(File folder,int depth){
        List<File> children=new ArrayList<>();for(File file:repository.list(folder))if(file.isDirectory())children.add(file);
        LinearLayout row=new LinearLayout(getContext());row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(Math.min(depth,12)*12),0,dp(3),0);
        if(folder.equals(selected)){GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFDCEAFE);bg.setCornerRadius(dp(8));row.setBackground(bg);}
        TextView arrow=new TextView(getContext());arrow.setGravity(android.view.Gravity.CENTER);arrow.setText(children.isEmpty()?"":expanded.contains(folder.getAbsolutePath())?"▾":"▸");arrow.setTextSize(17);arrow.setTextColor(0xFF52647A);arrow.setContentDescription(folder.getName()+" 하위 폴더 펼치기");row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(46)));
        arrow.setOnClickListener(v->{if(!expanded.remove(folder.getAbsolutePath()))expanded.add(folder.getAbsolutePath());reload();});
        TextView name=new TextView(getContext());name.setText(folder.equals(repository.root)?"모든 문서":folder.getName());name.setTextSize(14);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setTextColor(folder.equals(selected)?0xFF2563EB:0xFF334155);name.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder_open,0,0,0);name.setCompoundDrawablePadding(dp(6));row.addView(name,new LinearLayout.LayoutParams(0,dp(46),1));name.setContentDescription("폴더 "+name.getText());name.setOnClickListener(v->{selected=folder;reload();listener.folder(folder);});
        rows.addView(row,new LinearLayout.LayoutParams(-1,dp(46)));if(expanded.contains(folder.getAbsolutePath()))for(File child:children)addFolder(child,depth+1);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
