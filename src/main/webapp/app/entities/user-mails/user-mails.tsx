import React, { useState, useEffect } from 'react';
import { connect } from 'react-redux';
import { Link, RouteComponentProps } from 'react-router-dom';
import { Button, Col, Row, Table } from 'reactstrap';
import { ICrudGetAllAction, TextFormat } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { IRootState } from 'app/shared/reducers';
import { getEntities } from './user-mails.reducer';
import { IUserMails } from 'app/shared/model/user-mails.model';
import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';

export interface IUserMailsProps extends StateProps, DispatchProps, RouteComponentProps<{ url: string }> {}

export const UserMails = (props: IUserMailsProps) => {
  useEffect(() => {
    props.getEntities();
  }, []);

  const { userMailsList, match, loading } = props;
  return (
    <div>
      <h2 id="user-mails-heading">
        User Mails
        <Link to={`${match.url}/new`} className="btn btn-primary float-right jh-create-entity" id="jh-create-entity">
          <FontAwesomeIcon icon="plus" />
          &nbsp; Create new User Mails
        </Link>
      </h2>
      <div className="table-responsive">
        {userMailsList && userMailsList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th>ID</th>
                <th>Sent Date</th>
                <th>Sent By</th>
                <th>Mail Type</th>
                <th>Sent From</th>
                <th>User</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {userMailsList.map((userMails, i) => (
                <tr key={`entity-${i}`}>
                  <td>
                    <Button tag={Link} to={`${match.url}/${userMails.id}`} color="link" size="sm">
                      {userMails.id}
                    </Button>
                  </td>
                  <td>{userMails.sentDate ? <TextFormat type="date" value={userMails.sentDate} format={APP_DATE_FORMAT} /> : null}</td>
                  <td>{userMails.sentBy}</td>
                  <td>{userMails.mailType}</td>
                  <td>{userMails.sentFrom}</td>
                  <td>{userMails.userLogin ? userMails.userLogin : ''}</td>
                  <td className="text-right">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`${match.url}/${userMails.id}`} color="info" size="sm">
                        <FontAwesomeIcon icon="eye" /> <span className="d-none d-md-inline">View</span>
                      </Button>
                      <Button tag={Link} to={`${match.url}/${userMails.id}/edit`} color="primary" size="sm">
                        <FontAwesomeIcon icon="pencil-alt" /> <span className="d-none d-md-inline">Edit</span>
                      </Button>
                      <Button tag={Link} to={`${match.url}/${userMails.id}/delete`} color="danger" size="sm">
                        <FontAwesomeIcon icon="trash" /> <span className="d-none d-md-inline">Delete</span>
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && <div className="alert alert-warning">No User Mails found</div>
        )}
      </div>
    </div>
  );
};

const mapStateToProps = ({ userMails }: IRootState) => ({
  userMailsList: userMails.entities,
  loading: userMails.loading,
});

const mapDispatchToProps = {
  getEntities,
};

type StateProps = ReturnType<typeof mapStateToProps>;
type DispatchProps = typeof mapDispatchToProps;

export default connect(mapStateToProps, mapDispatchToProps)(UserMails);
