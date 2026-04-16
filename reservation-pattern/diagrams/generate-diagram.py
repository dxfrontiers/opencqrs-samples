#!/usr/bin/env python3
import graphviz

CMD_BG, CMD_BORDER, CMD_FONT = "#6699CC", "#336699", "#1a3350"
EVT_BG, EVT_BORDER, EVT_FONT = "#CC8844", "#996633", "#4d2e0f"
STATE_BG, STATE_BORDER = "#557755", "#2d5a2d"
NEG_BG, NEG_BORDER = "#aa4444", "#772222"

ARROW_CMD = "#336699"
ARROW_EVT = "#996633"
ARROW_SR = "#2d5a2d"
START_BG = "#1f2937"
FONT = "Helvetica"


def cmd(g, n, label):
    g.node(n, label, shape="hexagon", style="filled",
           fillcolor=CMD_BG, color=CMD_BORDER, penwidth="2",
           fontname=FONT, fontsize="10", fontcolor=CMD_FONT,
           margin="0.12,0.06")


def evt(g, n, label):
    g.node(n, label, shape="hexagon", style="filled",
           fillcolor=EVT_BG, color=EVT_BORDER, penwidth="2",
           fontname=FONT, fontsize="10", fontcolor=EVT_FONT,
           margin="0.12,0.06")


def state(g, n, label):
    g.node(n, label, shape="parallelogram", style="filled,bold",
           fillcolor=STATE_BG, color=STATE_BORDER, penwidth="3",
           fontname=FONT, fontsize="10", fontcolor="white",
           margin="0.14,0.07")


def neg_state(g, n, label):
    g.node(n, label, shape="parallelogram", style="filled,bold",
           fillcolor=NEG_BG, color=NEG_BORDER, penwidth="3",
           fontname=FONT, fontsize="10", fontcolor="white",
           margin="0.14,0.07")


def cmd_edge(g, a, b):
    g.edge(a, b, color=ARROW_CMD, penwidth="1.5",
           arrowhead="vee", arrowsize="0.8")


def evt_edge(g, a, b):
    g.edge(a, b, color=ARROW_EVT, penwidth="1.5",
           arrowhead="vee", arrowsize="0.8")


def rebuild_edge(g, a, b):
    g.edge(a, b, style="dashed", color=ARROW_SR, penwidth="1.5",
           arrowhead="vee", arrowsize="0.7")


def branch_edge(g, a, b, label):
    g.edge(a, b, xlabel=label, fontname=FONT, fontsize="8",
           fontcolor="#888888", color=ARROW_CMD, penwidth="1.5",
           arrowhead="vee", arrowsize="0.8")


def signup_diagram():
    d = graphviz.Digraph("signup", format="svg")
    d.attr(rankdir="TB", bgcolor="white", fontname=FONT,
           pad="0.4", nodesep="0.35", ranksep="0.4",
           label="Sign-Up Workflow", labelloc="t", fontsize="14")

    d.node("s0", "", shape="circle", width="0.25", height="0.25",
           style="filled", fillcolor=START_BG, color=START_BG)

    cmd(d, "cmd_signup", "SignUpCommand")
    evt(d, "evt_init", "SignUpInitiatedEvent")
    state(d, "ua_reg", "UserAccount\nRegistering(email)")
    cmd(d, "cmd_reserve", "ReserveEmailAddressCmd")

    evt(d, "evt_reserved", "EmailAddressReservedEvent")
    state(d, "ea_reserved", "EmailAddress\nReserved(email, username)")
    evt(d, "evt_completed", "SignUpCompletedEvent")
    state(d, "ua_registered", "UserAccount\nRegistered(email)")

    evt(d, "evt_denied", "EmailAddressDeniedEvent")
    evt(d, "evt_rejected", "SignUpRejectedEvent")
    neg_state(d, "ua_notreg", "UserAccount\nNotRegistered(email)")

    cmd_edge(d, "s0", "cmd_signup")
    evt_edge(d, "cmd_signup", "evt_init")
    rebuild_edge(d, "evt_init", "ua_reg")
    cmd_edge(d, "ua_reg", "cmd_reserve")

    evt_edge(d, "cmd_reserve", "evt_reserved")
    rebuild_edge(d, "evt_reserved", "ea_reserved")
    evt_edge(d, "ea_reserved", "evt_completed")
    rebuild_edge(d, "evt_completed", "ua_registered")

    branch_edge(d, "cmd_reserve", "evt_denied", "Reserved")
    evt_edge(d, "evt_denied", "evt_rejected")
    rebuild_edge(d, "evt_rejected", "ua_notreg")

    for pair in [
        ("evt_reserved", "evt_denied"),
        ("ea_reserved", "evt_rejected"),
        ("evt_completed", "ua_notreg"),
        ("ua_registered", "ua_notreg"),
    ]:
        with d.subgraph() as r:
            r.attr(rank="same")
            for n in pair:
                r.node(n)

    d.render("signup", cleanup=True)
    print("signup.svg generated")


def change_email_diagram():
    d = graphviz.Digraph("change-email", format="svg")
    d.attr(rankdir="TB", bgcolor="white", fontname=FONT,
           pad="0.4", nodesep="0.35", ranksep="0.4",
           label="Change-Email Workflow", labelloc="t", fontsize="14")

    state(d, "ua_active", "UserAccount\nRegistered(email)")
    cmd(d, "cmd_change", "ChangeEmailCommand")
    evt(d, "evt_change_init", "EmailChangeInitiatedEvent")
    state(d, "ua_changing", "UserAccount\nChangingEmail(email, newEmail)")
    cmd(d, "cmd_reserve", "ReserveEmailAddressCmd")

    evt(d, "evt_reserved", "EmailAddressReservedEvent")
    state(d, "ea_reserved", "EmailAddress\nReserved(newEmail, username)")
    evt(d, "evt_completed", "EmailChangeCompletedEvent")
    state(d, "ua_registered_new", "UserAccount\nRegistered(newEmail)")
    cmd(d, "cmd_release", "ReleaseEmailAddressCmd")
    evt(d, "evt_released", "EmailAddressReleasedEvent")
    state(d, "ea_available", "EmailAddress\nAvailable(oldEmail)")

    evt(d, "evt_denied", "EmailAddressDeniedEvent")
    evt(d, "evt_reverted", "EmailChangeRevertedEvent")
    state(d, "ua_reverted", "UserAccount\nRegistered(email)")

    cmd_edge(d, "ua_active", "cmd_change")
    evt_edge(d, "cmd_change", "evt_change_init")
    rebuild_edge(d, "evt_change_init", "ua_changing")
    cmd_edge(d, "ua_changing", "cmd_reserve")

    evt_edge(d, "cmd_reserve", "evt_reserved")
    rebuild_edge(d, "evt_reserved", "ea_reserved")
    evt_edge(d, "ea_reserved", "evt_completed")
    rebuild_edge(d, "evt_completed", "ua_registered_new")
    cmd_edge(d, "ua_registered_new", "cmd_release")
    evt_edge(d, "cmd_release", "evt_released")
    rebuild_edge(d, "evt_released", "ea_available")

    branch_edge(d, "cmd_reserve", "evt_denied", "Reserved")
    evt_edge(d, "evt_denied", "evt_reverted")
    rebuild_edge(d, "evt_reverted", "ua_reverted")

    for pair in [
        ("evt_reserved", "evt_denied"),
        ("ea_reserved", "evt_reverted"),
        ("evt_completed", "ua_reverted"),
        ("ua_registered_new", "ua_reverted"),
    ]:
        with d.subgraph() as r:
            r.attr(rank="same")
            for n in pair:
                r.node(n)

    d.render("change-email", cleanup=True)
    print("change-email.svg generated")


if __name__ == "__main__":
    signup_diagram()
    change_email_diagram()
