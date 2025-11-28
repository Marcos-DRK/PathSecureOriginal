package com.tuempresa.pathsecure;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class RoutesAdapter extends RecyclerView.Adapter<RoutesAdapter.ViewHolder> {

    private List<Route> routeList;
    private OnRouteActionListener listener;

    public interface OnRouteActionListener {
        void onViewRoute(Route route);
        void onDeleteRoute(Route route);
        void onEditRoute(Route route);
    }

    public RoutesAdapter(List<Route> routeList, OnRouteActionListener listener) {
        this.routeList = routeList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_route, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Route route = routeList.get(position);
        holder.tvName.setText(route.getName());
        holder.tvDate.setText(route.getDate());

        holder.itemView.setOnClickListener(v -> listener.onViewRoute(route));

        holder.btnDelete.setOnClickListener(v -> listener.onDeleteRoute(route));
        holder.btnEdit.setOnClickListener(v -> listener.onEditRoute(route));
    }

    @Override
    public int getItemCount() { return routeList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDate;
        ImageButton btnDelete, btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvRouteName);
            tvDate = itemView.findViewById(R.id.tvRouteDate);
            btnDelete = itemView.findViewById(R.id.btnDeleteRoute);
            btnEdit = itemView.findViewById(R.id.btnEditRoute);
        }
    }
}