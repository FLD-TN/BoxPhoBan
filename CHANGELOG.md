# Changelog

## 0.2.1 (2026-11-09)

Vào phó bản nhanh hơn, đỡ tốn CPU lúc chuẩn bị.

- Paste/dọn ô phó bản không ghi lịch sử undo của FAWE nữa (trước đây FAWE nén từng block đã đổi xuống đĩa, đây là
  phần tốn CPU nhất lúc chờ), tắt cập nhật block lân cận/vật lý khi paste.
- Dọn ô (đặt về không khí khi kết thúc) không tính lại ánh sáng; lần paste sau tự tính lại.
- Schematic được giữ trong RAM sau lần đọc đầu, các lượt sau không phải đọc + giải nén file lại
  (tự đọc lại khi file đổi, ví dụ sau khi lưu trong editor).
- Log thời gian paste nếu quá 1 giây (`Paste <file>: đọc Xms, đặt block Yms`) để dễ đo.

## 0.2.0 (2026-11-08)

Tường chuyển động và khoá phòng.

- Hành động **Đóng tường / Mở tường**: tường trượt mượt (BlockDisplay) theo 3 kiểu: `down` hạ từ trần như cửa song sắt,
  `up` trồi từ sàn, `side` hai nửa trượt ngang khép vào giữa. Thời gian và âm thanh chỉnh được.
  Lúc đóng, block chắn vô hình đặt ngay nên không ai lọt qua; ai đứng giữa lối bị đẩy về chỗ node.
- Vùng tường dựng ở vị trí ĐÓNG trong editor. Khi phó bản bắt đầu tường được gỡ (lối đi thoáng), trừ vùng đặt
  `/pba region wall <vùng> closed`.
- Hành động **Khoá phòng** (vùng phòng, cửa vào, cửa ra): người đầu tiên vào thì cửa ra đóng; mọi người còn sống đã vào
  thì cửa vào đóng; không còn ai sống trong phòng thì cửa vào mở lại (tránh kẹt khi cả đội chết); dọn sạch ải thì
  mở hết. Hành động **Mở khoá phòng** để mở tay.
- Trigger **Phòng khoá kín** (cửa vào vừa đóng, dùng để spawn quái) và **Dọn sạch ải** (mọi quái spawn trong ải đã chết,
  chờ cả các node có độ trễ).
- Paste phòng bỏ qua block không khí (nhanh hơn nhiều với map rộng).
- Trigger **Tương tác** và **Dùng chìa** có thêm `region`: bấm block nào trong vùng cũng được (ví dụ cả bức cổng).
  Nhận chìa thì cả đội được báo ai đang giữ chìa.
- **Nhóm ngẫu nhiên** cho node: mỗi lượt giữ ngẫu nhiên K node cùng nhóm, các node khác tắt và block của chúng bị gỡ
  (rương chìa đổi chỗ mỗi lượt).
- Trigger **Giữ vị trí**: đứng trong vùng đủ số giây (về 0 hoặc tạm dừng khi rời vùng), viền hạt + tiến độ actionbar,
  placeholder `%boxphoban_hold%`.
- Giữ vị trí có holo DecentHolograms (tên vị trí + thanh tiến độ %), placeholder `%boxphoban_hold_percent%`,
  `%boxphoban_hold_bar%`, `%boxphoban_hold_name%`; bật tắt trong config.yml `hold.*` hoặc từng node, chữ trong messages.yml `hold.*`.
- Hành động **Đổi mục tiêu**: đổi dòng nhiệm vụ trên scoreboard giữa chừng trong một ải (tự về mục tiêu của ải khi
  chuyển ải).

## 0.1.0 (2026-11-08)

Bản đầu tiên theo [PHOBAN_SPEC.md](PHOBAN_SPEC.md).

- Phòng (= party): tạo, mời (bấm chat), vào phòng công khai, kick, chuyển chủ phòng, công khai / riêng tư, đổi độ khó,
  tự giải tán khi chờ lâu. Không có hàng chờ.
- Vào phòng: miễn phí, vé (chủ phòng trả hoặc mỗi người), phí $, quyền, phải clear phó bản khác, cooldown
  TIMER / DAILY / WEEKLY (giờ VN), lượt mỗi ngày. Vé và phí trừ khi bắt đầu, hoàn lại nếu tạo phó bản lỗi.
- Instance: paste schematic bằng FAWE vào ô lưới trong world void `boxphoban`, giới hạn toàn server + từng phó bản,
  dọn ô sau khi xong, ô còn sót sau khi sập được dọn lúc khởi động.
- Editor trong game: công cụ node / vùng / điểm / hiện, import vùng WorldEdit, khung map, điểm bắt đầu, điểm, ải.
  10 trigger, 5 điều kiện, 16 hành động (mô hình Trigger + Condition + Function của MythicDungeons).
- Độ khó: level MythicMobs, nhân máu, nhân sát thương, thời gian giới hạn, bảng thưởng riêng.
- Mạng SHARED / PERSONAL / NONE, hồi sinh ở checkpoint sau vài giây, hết mạng thành khán giả.
- Snapshot người chơi trước khi vào (vị trí, gamemode, bay, máu, đói), trả lại ở mọi đường ra:
  thắng, thua, rời, đổi world, thoát game, sập server, restart.
- Luôn giữ đồ (gamerule + ép trong sự kiện chết). Chặn lệnh ngoài whitelist, vứt đồ, phá / đặt block, mở rương có
  sẵn, chuột phải người chơi (trade), PvP trong phòng, ender pearl / spectator teleport ra ngoài khung.
- Thưởng: tiền, gem BoxCore, lệnh, item, thưởng lần clear đầu, loot rương riêng từng người giữ trong túi thưởng
  phó bản tới khi thắng, túi đầy thì vào kho thưởng chờ. Khán giả lúc thắng vẫn nhận, người rời không nhận gì.
- GUI: danh sách phó bản, độ khó, danh sách phòng, phòng của bạn, kết quả, rương thưởng, kho thưởng chờ, editor.
- PlaceholderAPI cho scoreboard TAB (file mẫu `TAB_scoreboard_phoban.yml`), bossbar máu boss.
- Admin: test, list, stop, watch (xem dạng khán giả), phát vé, thêm item thưởng / loot, reset cooldown, reload.

Ghi chú so với spec:
- Hiệu ứng thuốc không được lưu / trả lại (giữ nguyên, để không đụng hiệu ứng giáp CE).
- Người đang offline (trong thời gian chờ disconnect) đúng lúc phòng thắng không nhận thưởng.
- Bảng thưởng mặc định của mỗi độ khó tên `<id phó bản>_<id độ khó>`, cần tạo trong rewards.yml.
