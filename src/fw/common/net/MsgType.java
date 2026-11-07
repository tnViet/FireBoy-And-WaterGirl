package fw.common.net;

public class MsgType {
    public static final String REGISTER = "REGISTER";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAILED = "REGISTER_FAILED";
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String LOGOUT_SUCCESS = "LOGOUT_SUCCESS";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final String GET_RANK = "GET_RANK";
    public static final String RANK = "RANK";
    // Lịch sử các màn mình đã hoàn thành (mới nhất trước): GET_HISTORY {limit} -> HISTORY {rows}
    public static final String GET_HISTORY = "GET_HISTORY";
    public static final String HISTORY = "HISTORY";
    // Thống kê từng màn của 1 người chơi: GET_STATS {acc} -> STATS {acc, rows}
    public static final String GET_STATS = "GET_STATS";
    public static final String STATS = "STATS";
    // Chat trong phòng: client gửi CHAT {text}, server gửi CHAT {acc, name, text} cho cả phòng
    public static final String CHAT = "CHAT";
    // Chat tổng ở sảnh: client gửi LOBBY_CHAT {text}, server gửi LOBBY_CHAT {acc, name, text} cho mọi người đang online
    public static final String LOBBY_CHAT = "LOBBY_CHAT";
    public static final String CREATE_ROOM = "CREATE_ROOM";
    public static final String JOIN_ROOM = "JOIN_ROOM";
    public static final String LEAVE_ROOM = "LEAVE_ROOM";
    public static final String LEFT_ROOM = "LEFT_ROOM";
    public static final String SELECT_LEVEL = "SELECT_LEVEL";
    public static final String SWAP = "SWAP";
    public static final String RESTART = "RESTART";
    public static final String START_GAME = "START_GAME";
    // Bỏ phiếu sau khi kết thúc màn: client gửi VOTE {choice: REPLAY | NEXT},
    // server gửi VOTE_STATE {votes: [{acc, choice}]} cho cả phòng; đủ 2 phiếu giống nhau thì bắt đầu
    public static final String VOTE = "VOTE";
    public static final String VOTE_STATE = "VOTE_STATE";
    public static final String ERR = "ERR";
    public static final String LEVELS = "LEVELS";
    public static final String ONLINE_LIST = "ONLINE_LIST";
    public static final String ROOM_LIST = "ROOM_LIST";
    public static final String ROOM_INFO = "ROOM_INFO";
    public static final String GAME_START = "GAME_START";
    public static final String GAME_GO = "GAME_GO";
    public static final String KEY_COLLECTED = "KEY_COLLECTED";
    public static final String DOOR_PASSED = "DOOR_PASSED";
    public static final String LEVER_TOGGLED = "LEVER_TOGGLED";
    public static final String PLATE_CHANGED = "PLATE_CHANGED";
    public static final String MECH_MOVING = "MECH_MOVING";
    public static final String PLAYER_DIED = "PLAYER_DIED";
    public static final String LEVEL_COMPLETE = "LEVEL_COMPLETE";
    public static final String LEVEL_FAILED = "LEVEL_FAILED";
    public static final String GAME_ABORT = "GAME_ABORT";
    public static final String UDP_HELLO = "UDP_HELLO";
    public static final String UDP_ACK = "UDP_ACK";
    public static final String INPUT = "INPUT";
    public static final String SNAP = "SNAP";
}
